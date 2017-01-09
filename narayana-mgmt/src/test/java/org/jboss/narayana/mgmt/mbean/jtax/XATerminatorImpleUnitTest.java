/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors 
 * as indicated by the @author tags. 
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors. 
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 * 
 * (C) 2005-2006,
 * @author JBoss Inc.
 */
/*
 * Copyright (C) 2001, 2002,
 *
 * Hewlett-Packard Arjuna Labs,
 * Newcastle upon Tyne,
 * Tyne and Wear,
 * UK.
 *
 * $Id: SimpleTest.java 2342 2006-03-30 13:06:17Z  $
 */

package org.jboss.narayana.mgmt.mbean.jtax;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.internal.jta.recovery.arjunacore.RecoveryXids;
import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinateTransaction;
import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.SubordinationManager;
import com.arjuna.ats.internal.jta.transaction.arjunacore.jca.TransactionImporter;
import com.arjuna.ats.internal.jta.transaction.jts.TransactionImple;
import com.arjuna.ats.internal.jts.ControlWrapper;
import com.arjuna.ats.internal.jts.orbspecific.ControlImple;
import com.arjuna.ats.jta.recovery.XAResourceRecoveryHelper;
import com.arjuna.ats.jta.xa.XidImple;
import com.arjuna.ats.jts.extensions.AtomicTransaction;
import com.arjuna.ats.internal.jts.orbspecific.interposition.coordinator.ServerTransaction;
import com.arjuna.ats.internal.jta.transaction.jts.jca.XATerminatorImple;
import com.arjuna.ats.internal.jta.resources.jts.orbspecific.XAResourceRecord;
import com.arjuna.ats.internal.jta.recovery.jts.XARecoveryModule;

import org.jboss.narayana.mgmt.internal.arjuna.ObjStoreBrowser;
import org.jboss.narayana.mgmt.mbean.LogBrowser;
import org.jboss.narayana.mgmt.util.JMXServer;

import javax.management.ObjectName;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class XATerminatorImpleUnitTest extends TestBase
{
    private Xid failedResourceXid;
    private ObjStoreBrowser osb;

	@Before
	public void beforeTest () throws Exception {
        osb = LogBrowser.getBrowser(null).getImpl();

 //       osb.setType("        /StateManager/BasicAction/TwoPhaseCoordinator/ArjunaTransactionImple/ServerTransaction/JCA

        osb.viewSubordinateAtomicActions(true);
        osb.setExposeAllRecordsAsMBeans(true);
    }

    @After
    public void afterTest () throws Exception {
        osb.stop();
    }

    @Test
    public void testXARMERR () throws Exception {
        Uid uid = new Uid();
        XidImple xid = new XidImple(uid);
        TransactionImporter imp = SubordinationManager.getTransactionImporter();

        SubordinateTransaction subordinateTransaction = imp.importTransaction(xid);
        // This is required because it JTS records are stored with a dynamic _savingUid
        // Normally they are recovered using XATerminator but for this test I would like to stick to testing
        // transaction importer
        Field field = TransactionImple.class.getDeclaredField("_theTransaction");
        field.setAccessible(true);
        Object o = field.get(subordinateTransaction);
        field = AtomicTransaction.class.getDeclaredField("_theAction");
        field.setAccessible(true);
        o = field.get(o);
        field = ControlWrapper.class.getDeclaredField("_controlImpl");
        field.setAccessible(true);
        o = field.get(o);
        field = ControlImple.class.getDeclaredField("_transactionHandle");
        field.setAccessible(true);
        o = field.get(o);
        field = ServerTransaction.class.getDeclaredField("_savingUid");
        field.setAccessible(true);
        Uid savingUid = (Uid) field.get(o);

        subordinateTransaction.enlistResource(new TestXAResource() {
            @Override
            public void commit(Xid xid, boolean b) throws XAException {
                this.xid = null;
            }

            @Override
            public int prepare(Xid xid) throws XAException {
                return 0;
            }

            @Override
            public void rollback(Xid xid) throws XAException {
                fail("Resource was rolled back");
            }
        });

        subordinateTransaction.enlistResource(new TestXAResource() {
            @Override
            public void commit(Xid xid, boolean b) throws XAException {
                throw new XAException(XAException.XA_HEURHAZ);
            }

            @Override
            public int prepare(Xid xid) throws XAException {
                failedResourceXid = xid;
                return 0;
            }

            @Override
            public void rollback(Xid xid) throws XAException {
                fail("Resource was rolled back");
            }
        });

        XATerminatorImple xa = new XATerminatorImple();
        xa.prepare(xid);
        try {
            xa.commit(xid, false);
            fail();
        } catch (final XAException ex) {
            assertTrue(ex.errorCode == XAException.XA_HEURMIX);
        }
        try {
            xa.commit(xid, false);
        } catch (XAException e) {
            assertTrue(e.errorCode == XAException.XA_RETRY);
        }

        osb.probe();

        String type = com.arjuna.ats.internal.jta.transaction.jts.subordinate.jca.coordinator.ServerTransaction.getType();
        String onFmt = "jboss.jta:type=ObjectStore,itype=%s,uid=%s,puid=*";
        String onQuery = String.format(onFmt, type.substring(1), savingUid.stringForm().replaceAll(":", "_"));

        Set<ObjectName> participants = JMXServer.getAgent().queryNames(onQuery, null);
        assertEquals(1, participants.size());
        JMXServer.getAgent().getServer().invoke(participants.iterator().next(), "clearHeuristic", null, null);
        xa.recover(XAResource.TMSTARTRSCAN);
        xa.recover(XAResource.TMENDRSCAN);



        Set<ObjectName> xaResourceRecords = JMXServer.getAgent().queryNames("jboss.jta:type=ObjectStore,itype=" + XAResourceRecord.typeName().substring(1) +",uid=*", null);
        for (ObjectName xaResourceRecord : xaResourceRecords) {

            Object getGlobalTransactionId = JMXServer.getAgent().getServer().getAttribute(xaResourceRecord, "GlobalTransactionId");
            Object getBranchQualifier = JMXServer.getAgent().getServer().getAttribute(xaResourceRecord, "BranchQualifier");

            if (Arrays.equals(failedResourceXid.getGlobalTransactionId(), (byte[]) getGlobalTransactionId) && Arrays.equals(failedResourceXid.getBranchQualifier(), (byte[]) getBranchQualifier)) {

                Object getHeuristicValue = JMXServer.getAgent().getServer().getAttribute(xaResourceRecord, "HeuristicValue");
                assertTrue(getHeuristicValue.equals(6));
                JMXServer.getAgent().getServer().invoke(xaResourceRecord, "clearHeuristic", null, null);
            }

        }
        XARecoveryModule xaRecoveryModule = new XARecoveryModule();
        xaRecoveryModule.addXAResourceRecoveryHelper(new XAResourceRecoveryHelper() {
            @Override
            public boolean initialise(String p) throws Exception {
                return false;
            }

            @Override
            public XAResource[] getXAResources() throws Exception {
                return new XAResource[] {
                        new TestXAResource()  {
                            public Xid[] recover(int var) throws XAException {
                                if (var == XAResource.TMSTARTRSCAN) {
                                    if (failedResourceXid != null) {
                                        return new Xid[]{failedResourceXid};
                                    }
                                }
                                return new Xid[0];
                            }
                            @Override
                            public void commit(Xid xid, boolean b) throws XAException {
                                failedResourceXid = null;
                            }

                            @Override
                            public int prepare(Xid xid) throws XAException {
                                return 0;
                            }

                            @Override
                            public void rollback(Xid xid) throws XAException {
                                fail("Resource was rolled back");
                            }
                        }
                };
            }
        });
        xaRecoveryModule.periodicWorkFirstPass();
        Field safetyIntervalMillis = RecoveryXids.class.getDeclaredField("safetyIntervalMillis");
        safetyIntervalMillis.setAccessible(true);
        Object o1 = safetyIntervalMillis.get(null);
        safetyIntervalMillis.set(null, 0);
        try {
            xaRecoveryModule.periodicWorkSecondPass();
        } finally {
            safetyIntervalMillis.set(null, o1);
        }

        xa.recover(XAResource.TMSTARTRSCAN);
        try {
            xa.commit(xid, false);
        } finally {
            xa.recover(XAResource.TMENDRSCAN);
        }
        assertNull(failedResourceXid);
    }
}
