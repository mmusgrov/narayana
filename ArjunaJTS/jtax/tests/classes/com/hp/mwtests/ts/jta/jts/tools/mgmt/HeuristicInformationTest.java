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
package com.hp.mwtests.ts.jta.jts.tools.mgmt;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.arjuna.coordinator.RecordType;
import com.arjuna.ats.arjuna.coordinator.TwoPhaseOutcome;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeManager;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeMap;
import com.arjuna.ats.arjuna.tools.osb.mbean.HeuristicStatus;
import com.arjuna.ats.arjuna.tools.osb.mbean.OSBTypeHandler;
import com.arjuna.ats.internal.arjuna.thread.ThreadActionData;
import com.arjuna.ats.internal.arjuna.tools.osb.ARTypeHandler;
import com.arjuna.ats.internal.arjuna.tools.osb.ARTypeHandlerImpl;
import com.arjuna.ats.internal.arjuna.tools.osb.TypeRepository;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;
import com.arjuna.ats.internal.jta.tools.osb.mbean.jts.osb.JTSARHandler;
import com.arjuna.ats.internal.jts.orbspecific.coordinator.ArjunaTransactionImple;
import com.hp.mwtests.ts.jta.jts.tools.UserExtendedCrashRecord;
import org.junit.Before;
import org.junit.Test;
import org.omg.CosTransactions.HeuristicHazard;

import javax.management.*;
import java.io.File;
import java.util.Collection;
import java.util.Set;

import static org.junit.Assert.*;

/**
 *
 * @author Mike Musgrove
 */

/**
 * @deprecated as of 5.0.5.Final In a subsequent release we will change packages names in order to 
 * provide a better separation between public and internal classes.
 */
@Deprecated // in order to provide a better separation between public and internal classes.
public class HeuristicInformationTest {//

    static class UserARTypeHandler extends ARTypeHandlerImpl {

        public UserARTypeHandler(String _type) {
            super(_type);
        }

        @Override
        public Collection<NamedOSEntryBeanMXBean> createRelatedMBeans(NamedOSEntryBeanMXBean bean) {
            return super.createRelatedMBeans(bean);
        }
    }

    @Before
    public void setup() {
        clearObjectStore();
    }

    public ObjStoreMgmt getOSB() {
        ARTypeHandler th = new UserARTypeHandler(UserExtendedCrashRecord.record_type());

        TypeRepository.registerTypeHandler(th, new JTSARHandler());

        ObjStoreMgmt osb = new ObjStoreMgmt();

        osb.start();

        return osb;
    }

    @Test
    public void heuristicInformationTest() throws Exception {
        ArjunaTransactionImple A = new ArjunaTransactionImple(null);
        int expectedHeuristic = TwoPhaseOutcome.HEURISTIC_ROLLBACK;
        ThreadActionData.purgeActions();

        UserExtendedCrashRecord recs[] = {
                new UserExtendedCrashRecord(UserExtendedCrashRecord.CrashLocation.NoCrash, UserExtendedCrashRecord.CrashType.Normal, null),
                new UserExtendedCrashRecord(UserExtendedCrashRecord.CrashLocation.CrashInCommit, UserExtendedCrashRecord.CrashType.HeuristicHazard,
                        new UserExtendedCrashRecord.HeuristicInformationOverride(expectedHeuristic)) // this value will override HeuristicHazard
        };

        RecordTypeManager.manager().add(new RecordTypeMap() {
            public Class<? extends AbstractRecord> getRecordClass () { return UserExtendedCrashRecord.class;}
            public int getType () {return RecordType.USER_DEF_FIRST1;}
        });

        A.start();

        for (UserExtendedCrashRecord rec : recs)
            A.add(rec);

        try {
            A.commit(true);
            fail("transaction commit should have produced a heuristic hazard");
        } catch (HeuristicHazard e) {
            // expected
        }

        ObjStoreMgmt osb = getOSB();

        osb.setExposeAllRecordsAsMBeans(true);
        osb.probe();

        // there should now be an MBean entry corresponding to a JTS record, read it via JMX:
        JMXServer server = JMXServer.getAgent();
        MBeanServer mbs = server.getServer();
        String typeName = ArjunaTransactionImple.typeName().substring(1);

        Set<ObjectName> txnOI = server.findMatchingBeans(A.get_uid(), null, typeName);
        Set<ObjectName> participants = server.findMatchingBeans(A.get_uid(), recs[1].get_uid(), typeName);

        assertTrue(txnOI.size() == 1);
        assertTrue(participants.size() == 1);

        for (ObjectName on : participants) {
            AttributeList al = mbs.getAttributes(on, new String[]{"Id", "Status", "HeuristicStatus", "GlobalTransactionId"});
            for (Attribute a : al.asList()) {
                if ("HeuristicStatus".equals(a.getName())) {
                    HeuristicStatus ahs = HeuristicStatus.valueOf(a.getValue().toString());
                    HeuristicStatus ehs = HeuristicStatus.intToStatus(expectedHeuristic);

                    // assert that the instrumented heuristic status has the expected value
                    assertTrue(ahs.equals(ehs));
                }
            }
        }
    }

    private void clearObjectStore() {
        final String objectStorePath = arjPropertyManager.getObjectStoreEnvironmentBean().getObjectStoreDir();
        final File objectStoreDirectory = new File(objectStorePath);

        clearDirectory(objectStoreDirectory);
    }

    private void clearDirectory(final File directory) {
        final File[] files = directory.listFiles();

        if (files != null) {
            for (final File file : files) {
                if (file.isDirectory()) {
                    clearDirectory(file);
                }

                file.delete();
            }
        }
    }

}
