/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package io.narayana.spi;

import io.narayana.spi.util.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;


import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.*;
import java.sql.*;
import java.util.Map;

public class SPIUnitTest
{
    @BeforeClass
    public static void setUp() throws Exception {
        JndiProvider.start();
        TransactionServiceFactory.start(true);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        TransactionServiceFactory.stop();
        JndiProvider.stop();
    }

    @Test(expected = RollbackException.class)
    public void testTimeout() throws Exception {
        final int timeout = 2;
        UserTransaction txn = (UserTransaction) new InitialContext().lookup(TransactionServiceFactory.getUserTransactionJNDIContext());

        txn.setTransactionTimeout(timeout);
        txn.begin();

        Thread.sleep(timeout * 1000 + 1000);
        txn.commit();
        fail("committing a timed out transaction should have thrown a RollbackException");
    }

    @Test
    public void testTransaction() throws Exception {
        UserTransaction txn = (UserTransaction) new InitialContext().lookup(TransactionServiceFactory.getUserTransactionJNDIContext());

        txn.begin();

        assertNotNull(txn);
        assertEquals(Status.STATUS_ACTIVE, txn.getStatus());

        txn.commit();

        // the transaction should have been disassociated
        assertEquals(Status.STATUS_NO_TRANSACTION, txn.getStatus());

        txn.begin();
        txn.commit();
        assertEquals(Status.STATUS_NO_TRANSACTION, txn.getStatus());
    }

    @Test
    public void testCommitXADS() throws Exception {
        final int NROWS = 3;
        UserTransaction txn = (UserTransaction) new InitialContext().lookup(TransactionServiceFactory.getUserTransactionJNDIContext());
//        UserTransaction txn = TransactionServiceFactory.getUserTransaction();
        Map<String, Connection> connections = TestUtils.getConnections();

        int c0 = TestUtils.countRows(connections.get("h2"), "CEYLONKV");
        int c1 = TestUtils.countRows(connections.get("postgresql"), "CEYLONKV");

        txn.begin();

        for (Map.Entry<String, Connection> entry : connections.entrySet()) {
            Connection connection = entry.getValue();

            for (int i = 0; i < NROWS; i++)
                TestUtils.insertTable(connection, "k" + i, "v" + i);

            PreparedStatement statement = connection.prepareStatement("SELECT * FROM CEYLONKV");
            ResultSet rs = statement.executeQuery();

            TestUtils.printResultSet(entry.getKey(), rs, "key", "val");

            rs.close();
            statement.close();
        }

        txn.commit();

        System.out.printf("TXN STATUS: %d%n", txn.getStatus());

        assertEquals(c0 + NROWS, TestUtils.countRows(connections.get("h2"), "CEYLONKV"));
        assertEquals(c1 + NROWS, TestUtils.countRows(connections.get("postgresql"), "CEYLONKV"));

        TestUtils.dropTables(connections);
    }

    @Test
    public void testAbortXADS() throws Exception {
        final int NROWS = 3;
        UserTransaction txn = (UserTransaction) new InitialContext().lookup(TransactionServiceFactory.getUserTransactionJNDIContext());
        Map<String, Connection> connections = TestUtils.getConnections();

        int c0 = TestUtils.countRows(connections.get("h2"), "CEYLONKV");
        int c1 = TestUtils.countRows(connections.get("postgresql"), "CEYLONKV");

        txn.begin();

        for (Map.Entry<String, Connection> entry : connections.entrySet()) {
            Connection connection = entry.getValue();

            for (int i = 0; i < NROWS; i++)
                TestUtils.insertTable(connection, "k" + i, "v" + i);

            PreparedStatement statement = connection.prepareStatement("SELECT * FROM CEYLONKV");
            ResultSet rs = statement.executeQuery();

            TestUtils.printResultSet(entry.getKey(), rs, "key", "val");

            rs.close();
            statement.close();
        }

        txn.rollback();

        System.out.printf("TXN STATUS: %d%n", txn.getStatus());

        assertEquals(c0, TestUtils.countRows(connections.get("h2"), "CEYLONKV"));
        assertEquals(c1, TestUtils.countRows(connections.get("postgresql"), "CEYLONKV"));

        TestUtils.dropTables(connections);
    }

    private void injectFault(ASFailureType type, ASFailureMode mode, String modeArg) throws RollbackException, SystemException, NamingException {
        ASFailureSpec fault = new ASFailureSpec("fault", mode, modeArg, type);
        TransactionManager transactionManager = (TransactionManager) new InitialContext().lookup(TransactionServiceFactory.getTransactionManagerJNDIContext());
        transactionManager.getTransaction().enlistResource(new DummyXAResource(fault));
    }

    @Test
    public void testXADSWithFaults() throws Exception {
        final int NROWS = 3;
//        boolean enlisted = false;
        Map<String, Connection> connections = TestUtils.getConnections();
        UserTransaction txn = (UserTransaction) new InitialContext().lookup(TransactionServiceFactory.getUserTransactionJNDIContext());
        int c0 = TestUtils.countRows(connections.get("h2"), "CEYLONKV");
        int c1 = TestUtils.countRows(connections.get("postgresql"), "CEYLONKV");

        txn.begin();

        // the first participant will throw a rollback exception during the commit phase resulting in a transaction rollback
        injectFault(ASFailureType.XARES_COMMIT, ASFailureMode.XAEXCEPTION, "XA_RBROLLBACK");

        for (Map.Entry<String, Connection> entry : connections.entrySet()) {
            Connection connection = entry.getValue();

            for (int i = 0; i < NROWS; i++)
                TestUtils.insertTable(connection, "k" + i, "v" + i);

//            if (!enlisted) {
//                injectFault(ASFailureType.XARES_COMMIT, ASFailureMode.XAEXCEPTION, "XA_HEURRB");
//
//                enlisted = true;
//            }

            PreparedStatement statement = connection.prepareStatement("SELECT * FROM CEYLONKV");
            ResultSet rs = statement.executeQuery();

            TestUtils.printResultSet(entry.getKey(), rs, "key", "val");

            rs.close();
            statement.close();
        }

        try {
            txn.commit();
        } catch (Exception e) {
            if (e instanceof HeuristicMixedException)
                System.out.printf("expected exception: " + e.getMessage());
            else if (e instanceof RollbackException)
                System.out.printf("expected exception: " + e.getMessage());
            else
                throw e;
        }
        //javax.transaction.Status;

        int pgRowCount = TestUtils.countRows(connections.get("postgresql"), "CEYLONKV");
        int h2RowCount = TestUtils.countRows(connections.get("h2"), "CEYLONKV");

        System.out.printf("TXN STATUS: %d H2: row count: %d PG: row count: %d%n", txn.getStatus(), h2RowCount, pgRowCount);

        assertEquals("H2: row count:", c0, h2RowCount);
        assertEquals("PG: row count:", c1, pgRowCount);

        TestUtils.dropTables(connections);
    }

    @Test
    public void testSynchronization() throws Exception {
        TestSynchronization synch =  new TestSynchronization() ;

        UserTransaction txn = (UserTransaction) new InitialContext().lookup(TransactionServiceFactory.getUserTransactionJNDIContext());
        TransactionManager transactionManager = (TransactionManager) new InitialContext().lookup(TransactionServiceFactory.getTransactionManagerJNDIContext());

        txn.begin();

        transactionManager.getTransaction().registerSynchronization(synch);

        txn.commit();

        assertTrue(synch.beforeCalled);
        assertTrue(synch.afterCalled);
    }

    class TestSynchronization implements Synchronization {
        boolean beforeCalled = false;
        boolean afterCalled = false;

        @Override
        public void beforeCompletion() {
            beforeCalled = true;
        }

        @Override
        public void afterCompletion(int i) {
            afterCalled = true;
        }
    }
}
