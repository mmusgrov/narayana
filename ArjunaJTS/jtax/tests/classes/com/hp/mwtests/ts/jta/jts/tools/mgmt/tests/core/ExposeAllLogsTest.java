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
package com.hp.mwtests.ts.jta.jts.tools.mgmt.tests.core;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.objectstore.RecoveryStore;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.arjuna.state.OutputObjectState;
import com.arjuna.ats.internal.arjuna.tools.osb.MBeanHelper;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.JMXServer;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.ObjStoreMgmt;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.tests.common.TestBase;
import org.junit.Test;

import javax.management.ObjectName;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test that the tooling can exposed all log record types
 *
 * @author Mike Musgrove
 */
public class ExposeAllLogsTest extends TestBase {
    private static final String FOO_TYPE = File.separator+"StateManager"+File.separator+"LockManager"+File.separator+"foo";

    @Test
    public void test1() throws Exception {
        test(true);
    }

    @Test
    public void test2() throws Exception {
        test(false);
    }

    private void test(boolean exposeAllLogsViaJMX) throws Exception {
        RecoveryStore store = StoreManager.getRecoveryStore();
        Set<Uid> uids;
        Map<Uid, ObjectName> uids2 = new HashMap<Uid, ObjectName>();
        JMXServer agent = JMXServer.getAgent();

        // create a record that by default the tooling does not expose
        byte[] data = new byte[10240];
        OutputObjectState state = new OutputObjectState();
        Uid u = new Uid();

        state.packBytes(data);
        assertTrue(store.write_committed(u, FOO_TYPE, state));

        // get uids via the object store API
        uids = getUids(store, new HashSet<Uid>(), FOO_TYPE);
        // and validate that there is a uid corresponding to u
        assertTrue(uids.contains(u));

        // Now start an object store browser configured to only expose the default set of logs
        ObjStoreMgmt browser = getOSB(false, false);//new UserARTypeHandler(UserExtendedCrashRecord.record_type()), new JTSARHandler());

        try {
            // check that the record is not exposed
            browser.probe();
            // lookup all MBeans
            getUids(uids2, agent.queryNames(JMXServer.STORE_MBEAN_NAME + ",*", null));

            // and validate that there is no MBean corresponding to u
            assertFalse(uids2.containsKey(u));

            browser.stop();

            // now try the same but tell the browser to expose all log records
            browser = getOSB(true, exposeAllLogsViaJMX);
            browser.probe();

            // and get the uids for log record MBeans
            uids2.clear();
            getUids(uids2, agent.queryNames(JMXServer.STORE_MBEAN_NAME + ",*", null));

            // and validate that there is now an MBean corresponding to u
            assertTrue(uids2.containsKey(u));

            // test that the MBean remove operation works
            agent.getServer().invoke(uids2.get(u), "remove", null, null);

            // check that both the log record and the MBean were removed
            uids.clear();
            getUids(store, uids, FOO_TYPE);
            assertFalse(uids.contains(u));

            uids2.clear();
            MBeanHelper.getUids(uids2, agent.queryNames(JMXServer.STORE_MBEAN_NAME + ",*", null));
            assertFalse(uids2.containsKey(u));
        } finally {
            browser.stop();
        }
    }
}
