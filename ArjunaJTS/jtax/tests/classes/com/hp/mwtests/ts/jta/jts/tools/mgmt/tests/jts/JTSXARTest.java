/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat Middleware LLC, and individual contributors
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
package com.hp.mwtests.ts.jta.jts.tools.mgmt.tests.jts;

import com.arjuna.ats.arjuna.tools.osb.mbean.HeuristicStatus;
import com.arjuna.ats.internal.arjuna.thread.ThreadActionData;
import com.arjuna.ats.internal.jta.resources.jts.orbspecific.XAResourceRecord;
import com.arjuna.ats.internal.jts.orbspecific.coordinator.ArjunaTransactionImple;
import com.arjuna.ats.internal.jts.resources.ExtendedResourceRecord;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.JMXServer;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.ObjStoreMBeanON;
import org.junit.Test;

import javax.management.*;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import java.util.Set;

import static org.junit.Assert.*;


import com.arjuna.ats.internal.jta.transaction.jts.TransactionImple;
import com.arjuna.ats.internal.jta.transaction.jts.TransactionManagerImple;

/**
 *
 * @author Mike Musgrove
 */
public class JTSXARTest extends JTSOSBTestBase {
    void injectCommitException(String exceptionType) throws Exception {
        XAFailureSpec fault = new XAFailureSpec(
                "JTSXARTest", XAFailureMode.XAEXCEPTION, XAFailureType.XARES_COMMIT, exceptionType, 0);
        XAException xae = XAFailureResource.getXAExceptionType(exceptionType);
        int expectedXAE = xae == null ? HeuristicStatus.UNKNOWN_XA_ERROR_CODE : xae.errorCode;

        TransactionManagerImple tm = new TransactionManagerImple();
        TransactionImple txn;
        XAFailureResource failureResource = new XAFailureResource(fault);

        // clean the thread that will create the transaction
        ThreadActionData.purgeActions();

        // begin a transaction
        tm.begin();
        txn = (TransactionImple) tm.getTransaction();

        // enlist two XA resources
        tm.getTransaction().enlistResource(new XAFailureResource());
        tm.getTransaction().enlistResource(failureResource);

        // commit the transaction and check for any expected exceptions
        try {
            tm.commit();
        } catch (HeuristicRollbackException e) {
            if (!XAFailureSpec.compatibleHeuristics(XAException.XA_HEURRB, expectedXAE))
                throw e;
        } catch (RollbackException e) {
            if (!XAFailureSpec.compatibleHeuristics(XAException.XA_RBROLLBACK, expectedXAE))
                throw e;
        } catch (HeuristicMixedException e) {
            if (!XAFailureSpec.compatibleHeuristics(XAException.XA_HEURMIX, expectedXAE))
                throw e;
        } catch (SystemException e) {
            if (!XAFailureSpec.compatibleHeuristics(XAException.XAER_RMFAIL, expectedXAE))
                throw e;
        }

        // Validate that the correct MBeans were created to instrument the transaction and participants
        // and validate they have the correct heuristic status
        osb.probe();

        Set<ObjectName> matchingBeans = JMXServer.getAgent().findMatchingBeans(null, null, "*");

        String parentType = ArjunaTransactionImple.typeName();
        String parentName = ObjStoreMBeanON.generateObjectName(parentType, txn.get_uid());
        ObjectName parentON = new ObjectName(parentName);
        assertTrue(matchingBeans.contains(parentON));
        // there should be an MBean for the transaction
        try {
            JMXServer.getAgent().getServer().getObjectInstance(parentON);
        } catch (InstanceNotFoundException e) {
            fail("transaction MBean was not created");
        }

        // There should be a JTS participant of type ExtendedResourceRecord corresponding to the XAFailureResource
        String childType = ObjStoreMBeanON.canonicalType(new ExtendedResourceRecord().type());
        Set<ObjectName> childBeans = JMXServer.getAgent().findMatchingBeans(txn.get_uid(), null, childType);
        assertEquals(1, childBeans.size());

        ObjectName childObjectName = childBeans.iterator().next();
        ObjStoreMBeanON childON = ObjStoreMBeanON.fromObjStoreMBeanON(childObjectName);

        // and there should be a JTS XAResourceRecord
        String childJTSType = XAResourceRecord.typeName();
        ObjStoreMBeanON  childJTSON = new ObjStoreMBeanON(childJTSType, childON.getParticipantUid());

        assertTrue(matchingBeans.contains(new ObjectName(childJTSON.getObjectName())));

        //"jboss.jta:type=ObjectStore,itype=StateManager/AbstractRecord/ExtendedResourceRecord,uid=0_ffff7f000001_a3c7_55a2cd49_3,puid=0_ffff7f000001_a3c7_55a2cd49_9"

        // JTS participants are of type ExtendedResourceRecord
//        String participantName= JMXServer.generateParticipantObjectName(childType, txn.get_uid(), failureResource.getUid());
//        ObjectName participantObjectNameON = new ObjectName(participantName);

        // there should now be an entry in the object store corresponding to the transaction
//        assertTrue(matchingBeans.contains(parentON));
        // and an entry for the ExtendedCrashRecord that got the heuristic
//        assertTrue(matchingBeans.contains(participantObjectNameON));


//        validateChildBeans(osb, w.getName(), 1, expectedXAE);

    }

    class ObjStoreMBeanKVPairs {
        public static final String OSB_BEAN_TYPE = "jboss.jta:type=ObjectStore";
        public static final String OSB_INST_KEY = "itype";
        public static final String OSB_UID_KEY = "itype";
        public static final String OSB_PARTICIPANT_KEY = "puid";

        private String type;
        private String uid;
        private String pUid;

        public boolean isOSBeanType(String objectName) {
            return objectName.startsWith(OSB_BEAN_TYPE);
        }

        public ObjStoreMBeanKVPairs(String objectName) throws MalformedObjectNameException {
            if (!isOSBeanType(objectName))
                throw new MalformedObjectNameException(objectName);

            for (String pair : objectName.split(",")) {
                String[] kv = pair.split("=");

                if (kv.length != 2)
                    throw new MalformedObjectNameException(objectName);

                if (OSB_INST_KEY.equals(kv[0]))
                    type = kv[1];
                else if (OSB_UID_KEY.equals(kv[0]))
                    uid = kv[1];
                else if (OSB_PARTICIPANT_KEY.equals(kv[0]))
                    pUid = kv[1];
            }
        }
    }


    @Test
    public void testXAR() throws Exception {
        injectCommitException("XAER_RMFAIL");
    }

    @Test
    public void testHeuristic() throws Exception {
        injectCommitException("XA_HEURRB");
    }

/*    private Set<UidWrapper> validateChildBeans(ObjStoreBrowser osb, String name, int expectedNumberOfChildBeans, int expectedXAE)
            throws MalformedObjectNameException, ReflectionException, InstanceNotFoundException, AttributeNotFoundException, MBeanException {
        MBeanServer mbs = JMXServer.getAgent().getServer();
        ObjectName txnON = new ObjectName(name);
        Object aid = mbs.getAttribute(txnON, "Id");
        assertNotNull(aid);
        Uid uidOfTxn = new Uid(aid.toString());
        Set<ObjectName> participants = mbs.queryNames(new ObjectName(name + ",puid=*"), null);

        assertEquals(expectedNumberOfChildBeans, participants.size());

        Set<UidWrapper> wrappers = new HashSet<UidWrapper>();

        for (ObjectName on : participants) {
            //mbs.getAttributes(on, new String[] {"Id", "Type", "Status", "HeuristicStatus", "FormatId", "GlobalTransactionId", "NodeName", "BranchQualifier"});
            AttributeList al = mbs.getAttributes(on, new String[] {"Id", "Status", "HeuristicStatus", "GlobalTransactionId"});

            for (Attribute a : al.asList()) {
                if ("Id".equals(a.getName())) {
                    Uid uid = new Uid(a.getValue().toString());
                    UidWrapper w = osb.findUid(uid);

                    // assert that the wrapper is in the mbean wrapper cache
                    assertNotNull(w);

                    wrappers.add(w);
                } else if ("GlobalTransactionId".equals(a.getName())) {
                    byte[] gtid = (byte[]) a.getValue();
                    Uid txOfXar = new Uid(gtid);

                    // assert that the gtid of the participant matches the parent action
                    assertEquals(txOfXar, uidOfTxn);
                } else if ("HeuristicStatus".equals(a.getName())) {
                    HeuristicStatus hs = HeuristicStatus.valueOf(a.getValue().toString());

                    // assert that the instrumented heuristic status has the expected value
                    assertEquals(hs.getXAErrorCode(), expectedXAE);
                }
            }
        }

        return wrappers;
    }*/
}
