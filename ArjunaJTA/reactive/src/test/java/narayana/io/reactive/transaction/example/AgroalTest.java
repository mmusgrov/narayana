package narayana.io.reactive.transaction.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.agroal.api.AgroalDataSource;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;

import narayana.io.reactive.transaction.ReactiveTransaction;
import narayana.io.reactive.transaction.internal.TransactionManagerImpl;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static java.lang.System.out;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests showing that commit/rollback/recovery functionality works fine
 * when Narayana is integrated with Agroal jdbc pooling library.
 */
//@RunWith(BMUnitRunner.class)
public class AgroalTest {
    private Connection connForVerification1, connForVerification2;

    @Before
    public void setUp() {
        connForVerification1 = AgroalH2Utils.getConnection(AgroalH2Utils.DB_1);
        connForVerification2 = AgroalH2Utils.getConnection(AgroalH2Utils.DB_2);
        AgroalH2Utils.dropTableSilently(connForVerification1);
        AgroalH2Utils.dropTableSilently(connForVerification2);
        AgroalH2Utils.createTable(connForVerification1);
        AgroalH2Utils.createTable(connForVerification2);
    }

    @After
    public void tearDown() throws Exception {
        // cleaning possible active global transaction
        TransactionManager txn = com.arjuna.ats.jta.TransactionManager.transactionManager();
        if(txn != null) {
            if(txn.getStatus() == jakarta.transaction.Status.STATUS_ACTIVE)
                txn.rollback();
            if(txn.getStatus() != jakarta.transaction.Status.STATUS_NO_TRANSACTION)
                txn.suspend();
        }
        try {
            connForVerification1.close();
            connForVerification2.close();
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
    }

    public void testWith() {
        String expected = "code block response";
        Function<ReactiveTransaction, Uni<String>> work = txn -> {
            out.printf("transactional work with txn=%s%n", txn);
            try {
                // the user work should be running with an active transaction
                assertNotNull("user code is not running transactionally",
                        com.arjuna.ats.jta.TransactionManager.transactionManager().getTransaction());
                assertEquals("user code is not running with an active transaction", Status.STATUS_ACTIVE,
                        com.arjuna.ats.jta.TransactionManager.transactionManager().getTransaction().getStatus());
            } catch (SystemException e) {
                fail(e.getMessage());
            }
            return Uni.createFrom().item(expected);
        };

        TransactionManagerImpl tm = new TransactionManagerImpl();
        String actual = tm
                .withTransaction(work)
                .onFailure().invoke(e -> fail(e.getMessage()))
                .await().indefinitely();

        assertEquals(expected, actual);
    }

    private static final String TEST_TABLE_NAME = "TXN_DRIVER_TEST";
    private static final String SELECT_QUERY = String.format("SELECT * FROM %s", TEST_TABLE_NAME);

    @Test
    public void testMultiWithTx() throws Exception {
        AgroalDatasource ag = new AgroalDatasource();
        record TxProj(int id, String name) {}

        ag.init();

        TransactionManagerImpl tm = new TransactionManagerImpl();
        Record expected = new TxProj(1, "jta");
        Function<ReactiveTransaction, Uni<Record>> dbUpdate = txn -> {
            try (Connection conn1 = ag.ds1.getConnection();
                 Connection conn2 = ag.ds2.getConnection();
                 PreparedStatement ps1 = conn1.prepareStatement(AgroalH2Utils.INSERT_STATEMENT);
                 PreparedStatement ps2 = conn2.prepareStatement(AgroalH2Utils.INSERT_STATEMENT)) {
                ps1.setInt(1, 0);
                ps1.setString(2, "JTA1");
                ps1.executeUpdate();
                ps2.setInt(1, 1);
                ps2.setString(2, "JTA2");
                ps2.executeUpdate();
            } catch (SQLException e) {
                fail(e.getMessage());
            }

            return Uni.createFrom().item(expected);
        };

        Record actual = tm
                .withTransaction(dbUpdate)
                .onFailure().invoke(e -> fail(e.getMessage()))
                .await().indefinitely();

        assertEquals(expected, actual);

        Multi<Record> results = resultSetAsMulti(AgroalH2Utils.select(connForVerification1));

        Iterable<Record> iterable = results.subscribe().asIterable();
        for (Record item : iterable) {
            System.out.printf("%s%n", item);
        }

        ag.closeConnections();
    }

    @Test
    public void testMulti() throws Exception {
        AgroalDatasource ag = new AgroalDatasource();

        ag.init();

        ag.transactionManager.begin();

        Connection conn1 = ag.ds1.getConnection();
        PreparedStatement ps1 = conn1.prepareStatement(AgroalH2Utils.INSERT_STATEMENT);

        ps1.setInt(1, 1);
        ps1.setString(2, "Arjuna");

        Connection conn2 = ag.ds2.getConnection();
        PreparedStatement ps2 = conn2.prepareStatement(AgroalH2Utils.INSERT_STATEMENT);

        ps2.setInt(1, 2);
        ps2.setString(2, "Narayana");

        try {
            ps1.executeUpdate();
            ps1.close();
            ps2.executeUpdate();
            ps2.close();
            ag.transactionManager.commit();
        } catch (Exception e) {
            ag.transactionManager.rollback();
            throw e;
        }
//        ag.process(() -> {});

        Multi<Record> results = resultSetAsMulti(AgroalH2Utils.select(connForVerification1));

        Iterable<Record> iterable = results.subscribe().asIterable();
        for (Record item : iterable) {
            System.out.printf("%s%n", item);
        }
    }

    @Test
    public void testStream() throws Exception {
        AgroalDatasource ag = new AgroalDatasource();

        ag.process(() -> {});

        try (Stream<Record> s = tableAsStream(ag.ds1, TEST_TABLE_NAME)) {
            // stream operation
            Object[] rows = s.toArray();
            for (Object row : rows) {
                System.out.printf("item: %s%n", row);
            }
        }

        ResultSet rs1 = connForVerification1.createStatement().executeQuery(SELECT_QUERY);

//        ResultSet rs1 = AgroalH2Utils.select(connForVerification1);
//        ResultSet rs2 = AgroalH2Utils.select(connForVerification2);

        assertTrue(rs1.next());

        Multi<Record> results = resultSetAsMulti(AgroalH2Utils.select(connForVerification2));

        Iterable<Record> iterable = results.subscribe().asIterable();
        for (Record item : iterable) {
            System.out.printf("%s%n", item);
        }

        //        rs1 = connForVerification1.createStatement().executeQuery(SELECT_QUERY);

        record TxProj(int id, String name) {}

        var p1 = new TxProj(rs1.getInt(1), rs1.getString(2));
        assertEquals(1, p1.id);
        assertEquals("Arjuna", p1.name);

        ag.closeConnections();
    }

    Record createRecord(ResultSet rs) throws SQLException {
        record TxProj(int id, String name) {}
        return new TxProj(rs.getInt(1), rs.getString(2));
    }

    interface UncheckedCloseable extends Runnable, AutoCloseable {
        default void run() {
            try { close(); } catch(Exception ex) { throw new RuntimeException(ex); }
        }
        static UncheckedCloseable wrap(AutoCloseable c) {
            return c::close;
        }
        default UncheckedCloseable nest(AutoCloseable c) {
            return ()->{ try(UncheckedCloseable c1=this) { c.close(); } };
        }
    }

    // based on article https://stackoverflow.com/questions/32209248/java-util-stream-with-resultset
    private Stream<Record> tableAsStream(AgroalDataSource dataSource, String table)
            throws SQLException {

        UncheckedCloseable close = null;
        String sql = "select * from " + table;

        try {
            Connection connection = dataSource.getConnection();

            close = UncheckedCloseable.wrap(connection);

            PreparedStatement pSt = connection.prepareStatement(sql);

            close = close.nest(pSt);
            connection.setAutoCommit(false);
            pSt.setFetchSize(5000);

            ResultSet resultSet = pSt.executeQuery();

            close = close.nest(resultSet);

            return StreamSupport.stream(new Spliterators.AbstractSpliterator<Record>(
                    Long.MAX_VALUE, Spliterator.ORDERED) {
                @Override
                public boolean tryAdvance(Consumer<? super Record> action) {
                    try {
                        if (!resultSet.next())
                            return false;

                        action.accept(createRecord(resultSet));

                        return true;
                    } catch(SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }, false).onClose(close);
        } catch(SQLException sqlEx) {
            if (close != null) {
                try {
                    close.close();
                } catch (Exception ex) {
                    sqlEx.addSuppressed(ex);
                }
            }

            throw sqlEx;
        }
    }

    Multi<Record> resultSetAsMulti(ResultSet resultSet) {
        Stream<Record> results =  StreamSupport.stream(new Spliterators.AbstractSpliterator<>(
                Long.MAX_VALUE, Spliterator.ORDERED) {
            @Override
            public boolean tryAdvance(Consumer<? super Record> action) {
                try {
                    if (!resultSet.next())
                        return false;

                    action.accept(createRecord(resultSet));

                    return true;
                } catch(SQLException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }, false);//.onClose(close);

        return Multi.createFrom().items(() -> StreamSupport.stream(results.spliterator(), false));
    }

    Stream<Record> resultSetAsStream(ResultSet resultSet) {
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<>(
                Long.MAX_VALUE, Spliterator.ORDERED) {
            @Override
            public boolean tryAdvance(Consumer<? super Record> action) {
                try {
                    if (!resultSet.next())
                        return false;

                    action.accept(createRecord(resultSet));

                    return true;
                } catch(SQLException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }, false);//.onClose(close);
    }

    @Test
    public void commit() throws Exception {
        AgroalDatasource ag = new AgroalDatasource();

        ag.process(() -> {});

    	ResultSet rs1 = AgroalH2Utils.select(connForVerification1);
    	ResultSet rs2 = AgroalH2Utils.select(connForVerification2);

    	Assert.assertTrue("First database does not contain data as expected to be commited",
                Objects.requireNonNull(rs1).next());

        record TxProj(int id, String name) {}

        var p1 = new TxProj(rs1.getInt(1), rs1.getString(2));
        assertEquals(1, p1.id);
        assertEquals("Arjuna", p1.name);

        Assert.assertTrue("Second database does not contain data as expected to be commited",
                Objects.requireNonNull(rs2).next());

        var p2 = new TxProj(rs2.getInt(1), rs2.getString(2));
        assertEquals(2, p2.id);
        assertEquals("Narayana", p2.name);

        ag.closeConnections();
    }
    
    @Test
    public void rollback() throws Exception {
        AgroalDatasource ag = new AgroalDatasource();

    	try {
    		ag.process(() -> {throw new RuntimeException("expected");});
    	} catch (Exception e) {
    	    checkException(e);
    	}

    	ResultSet rs1 = AgroalH2Utils.select(connForVerification1);
    	ResultSet rs2 = AgroalH2Utils.select(connForVerification2);

    	Assert.assertFalse("First database contains data which is not expected as rolled-back", rs1.next());
    	Assert.assertFalse("Second database contains data which is not expected as rolled-back", rs2.next());

    	ag.closeConnections();
    }


//    @BMScript("xaexception.rmfail")
//    @Test
    public void recovery() throws Exception {
        AgroalDatasource ag = new AgroalDatasource();

        ag.process(() -> {});

        ResultSet rs1 = AgroalH2Utils.select(connForVerification1);
        ResultSet rs2 = AgroalH2Utils.select(connForVerification2);
        Assert.assertFalse("Both databases [" + connForVerification1 + ", " + connForVerification2 +
            "] are committed even one was expected to fail with XAException",
            rs1.next() && rs2.next());

        // manually run recovery manager to check if integration with Agroal works
        // this verifies that XAResourceRecovery was setup and if recovery finishes the failed transaction
        ag.getRecoveryManager().scan();

        rs1 = AgroalH2Utils.select(connForVerification1);
        Assert.assertTrue("First database does not contain data as expected to be commited", rs1.next());

        //after recovery cycle we need a way to close XAResource
        //see https://issues.redhat.com/browse/JBTM-3325
        ag.closeConnectionsAfterRecovery();
    }

    private void checkException(Exception e) {
        if (!e.getMessage().toLowerCase().contains("expected"))
            Assert.fail("Exception message does not contain 'expected' but it's '"
                + e.getClass().getName() + ":" + e.getMessage() + "'");
    }
}