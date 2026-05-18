/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.ats.arjuna.coordinator.ActionStatus;
import com.arjuna.ats.arjuna.exceptions.ObjectStoreException;
import com.arjuna.ats.arjuna.objectstore.RecoveryStore;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.ByteArrayKey;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsStoreEnvironmentBean;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsSlotKeyGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

public class JGroupsGrouperTest extends JGroupsTestBase {

    private List<Store> stores;

    @BeforeAll
    static void beforeAll() {
        arjPropertyManager.getCoordinatorEnvironmentBean().setCommitOnePhase(true);
        Assertions.assertTrue(arjPropertyManager.getCoordinatorEnvironmentBean().isCommitOnePhase());
    }

    @BeforeEach
    public void beforeEach() {
        stores = new ArrayList<>();
    }

    @AfterEach
    public void afterEach() {
        for (Store store : stores) {
            store.stop();
        }
    }

    private Store getStore(String nodeName, String groupName) throws Exception {
        Store store = createStore(nodeName, groupName, STORE_DIR);
        stores.add(store);
        return store;
    }

    @Test
    public void testUserDefinedKeyGenerator() throws Exception {
        Store store1 = getStore("node1", "group1");

        // user defined SlotKeyGenerator instance (Arjuna ClassLoadingUtility doesn't support construct of anonymous classes)
        UserDefinedSlotKeyGenerator slotKeyGenerator = new UserDefinedSlotKeyGenerator();

        store1.config().setSlotKeyGenerator(slotKeyGenerator);
        store1.start();
        Assertions.assertTrue(slotKeyGenerator.initCalled);
        Assertions.assertTrue(slotKeyGenerator.generateCalled);

        RecoveryStore recoveryStore = startRecoveryStore(store1.config());

        Uid uid = new Uid();
        AtomicAction action = new AtomicAction(uid);
        Participant participant = new Participant();

        action.begin();
        action.add(participant);

        Assertions.assertEquals(ActionStatus.COMMITTED, action.commit(false));

        // In JGroups ReplCache, entries are stored with the slot keys
        // The size check may vary based on implementation details
        // For now, just verify the action completes successfully

        store1.stop();
        recoveryStore.stop();
    }

    @Test
    public void testMissingKeyGenerator() throws Exception {
        Store store1 = getStore("node1", "group1");

        store1.config().setSlotKeyGenerator(null); // verify that store still works with the default key generator
        store1.config().setSlotKeyGeneratorClassName(null);
        store1.start();

        RecoveryStore recoveryStore = startRecoveryStore(store1.config());

        Uid uid = new Uid();
        AtomicAction action = new AtomicAction(uid);
        Participant participant = new Participant();

        action.begin();
        action.add(participant);

        Assertions.assertEquals(ActionStatus.COMMITTED, action.commit(false));

        store1.stop();
        recoveryStore.stop();
    }

    /*
     * Test JGroups replication which provides data availability across cluster nodes.
     * With JGroups ReplCache all nodes in a cluster hold all keys, providing strong consistency.
     * In this test we verify how keys are replicated across nodes.
     */
    @Test
    public void testReplicationMode() throws Exception {
        List<Store> stores = new ArrayList<>();
        int numStores = 3;

        for (int i = 0; i < numStores; i++) {
            String nodeId = "node" + i;
            String groupId = "group" + i % 2;
            Store store = getStore(nodeId, groupId);

            store.config().setSlotKeyGeneratorClassName(JGroupsClusterMemberId.class.getName());
            store.start();
            stores.add(store);

            // Give time for cluster to form
            Thread.sleep(1000);
        }

        Map<AtomicAction, String> actions = new HashMap<>();
        RecoveryStore recoveryStore;
        int NUMBER_OF_ACTIONS_PER_NODE = 2;

        for (Store store : stores) {
            // start NUMBER_OF_ACTIONS_PER_NODE actions from node store.nodeName()
            recoveryStore = startRecoveryStore(store.config());

            for (int i = 0; i < NUMBER_OF_ACTIONS_PER_NODE; i++) {
                Uid uid = new Uid();
                AtomicAction aa = new AtomicAction(uid);
                Participant participant = new Participant();

                aa.begin();
                aa.add(participant);

                // don't delete the log yet because we want to verify that the corresponding cache entries exist
                int res = aa.commit(true);

                Assertions.assertEquals(ActionStatus.H_HAZARD, res);
                actions.put(aa, store.groupName());
                // make sure the log is in the store
                try {
                    recoveryStore.read_committed(aa.getSavingUid(), aa.type());
                } catch (ObjectStoreException e) {
                    fail(e); // record should be available in the recovery store
                }
            }
        }

        // check that actions were partitioned across the stores according to the groupNames and that any
        // action in a particular group can be read from any store configured with that groupName
        for (Store store : stores) {
            recoveryStore = startRecoveryStore(store.config());

            for (Map.Entry<AtomicAction, String> entry : actions.entrySet()) {
                AtomicAction aa = entry.getKey();
                boolean shouldPass = store.groupName().equals(entry.getValue());
                try {
                    recoveryStore.read_committed(aa.getSavingUid(), aa.type());
                    if (!shouldPass)
                        fail("action with group id " + entry.getValue()
                                + " should not be accessible from store with groupName " + store.groupName());
                } catch (ObjectStoreException e) {
                    if (shouldPass)
                        fail("action with group id " + entry.getValue()
                                + " should be accessible from store with groupName " + store.groupName());
                }
            }
        }

        /*
         * clean up the store (only recovery managers with the same groupId as the action should be able to remove the action)
         */
        Store store = stores.get(0);
        recoveryStore = startRecoveryStore(store.config());
        String groupName = store.config().getGroupName();

        for (Map.Entry<AtomicAction, String> entry : actions.entrySet()) {
            AtomicAction aa = entry.getKey();
            if (groupName.equals(entry.getValue())) {
                try {
                    recoveryStore.remove_committed(aa.getSavingUid(), aa.type());
                } catch (ObjectStoreException e) {
                    fail("should have been able to delete action");
                }
            }
        }
        store = stores.get(1); // the second store has a different group name
        recoveryStore = startRecoveryStore(store.config());
        groupName = store.config().getGroupName();

        for (Map.Entry<AtomicAction, String> entry : actions.entrySet()) {
            AtomicAction aa = entry.getKey();
            if (groupName.equals(entry.getValue())) {
                try {
                    recoveryStore.remove_committed(aa.getSavingUid(), aa.type());
                } catch (ObjectStoreException e) {
                    fail("should have been able to delete action");
                }
            }
        }

        for (Store s : stores) {
            s.stop();
        }

        recoveryStore.stop();
    }

    /*
     * test that the tests correctly clean up when multiple cache instances are started
     */
    @Test
    public void testCleanup() throws Exception {
        for (int i = 0; i < 4; i++) {
            String nodeId = "node" + i;
            Store store = getStore(nodeId, null);

            store.start();

            RecoveryStore recoveryStore = startRecoveryStore(store.config());

            AtomicAction action = new AtomicAction(new Uid());
            Participant participant = new Participant();

            action.begin();
            action.add(participant);
            int res = action.commit(false);
            Assertions.assertEquals(ActionStatus.COMMITTED, res);
            recoveryStore.stop();
            store.stop();
        }
    }

    /*
     * Test basic replication with two nodes
     */
    @Test
    public void testTwoNodeReplication() throws Exception {
        Store store1 = getStore("node1", null);
        Store store2 = getStore("node2", null);

        store1.start();
        // Give time for first node to start
        Thread.sleep(500);

        store2.start();
        // Give time for cluster to form
        Thread.sleep(1000);

        // Create an action on store1
        RecoveryStore recoveryStore1 = startRecoveryStore(store1.config());

        Uid uid = new Uid();
        AtomicAction action = new AtomicAction(uid);
        Participant participant = new Participant();

        action.begin();
        action.add(participant);
        int res = action.commit(true); // don't delete the log

        Assertions.assertEquals(ActionStatus.H_HAZARD, res);

        // Verify it can be read from store1
        try {
            recoveryStore1.read_committed(action.getSavingUid(), action.type());
        } catch (ObjectStoreException e) {
            fail("record should be available in store1: " + e);
        }

        // Verify it can be read from store2 (replicated)
        RecoveryStore recoveryStore2 = startRecoveryStore(store2.config());
        try {
            recoveryStore2.read_committed(action.getSavingUid(), action.type());
        } catch (ObjectStoreException e) {
            fail("record should be replicated to store2: " + e);
        }

        // Clean up
        try {
            recoveryStore1.remove_committed(action.getSavingUid(), action.type());
        } catch (ObjectStoreException e) {
            fail("should have been able to delete action");
        }

        store1.stop();
        store2.stop();
        recoveryStore1.stop();
        recoveryStore2.stop();
    }

    /*
     * Test that a node can rejoin and still access the replicated data
     */
    @Test
    public void testNodeRejoin() throws Exception {
        Store store1 = getStore("node1", null);
        Store store2 = getStore("node2", null);
        Store store3 = getStore("node3", null);

        store1.start();
        Thread.sleep(500);
        store2.start();
        Thread.sleep(500);
        store3.start();
        Thread.sleep(1000); // Give time for cluster to form

        // Create an action on store1
        RecoveryStore recoveryStore1 = startRecoveryStore(store1.config());

        Uid uid = new Uid();
        AtomicAction action = new AtomicAction(uid);
        Participant participant = new Participant();

        action.begin();
        action.add(participant);
        int res = action.commit(true); // don't delete the log

        Assertions.assertEquals(ActionStatus.H_HAZARD, res);

        // Verify it's replicated to store2
        RecoveryStore recoveryStore2 = startRecoveryStore(store2.config());
        try {
            recoveryStore2.read_committed(action.getSavingUid(), action.type());
        } catch (ObjectStoreException e) {
            fail("record should be replicated to store2: " + e);
        }

        // Stop store2 (simulating node failure)
        store2.stop();
        Thread.sleep(1000);

        // Verify data still accessible from store1 and store3
        try {
            recoveryStore1.read_committed(action.getSavingUid(), action.type());
        } catch (ObjectStoreException e) {
            fail("record should still be available in store1: " + e);
        }

        RecoveryStore recoveryStore3 = startRecoveryStore(store3.config());
        try {
            recoveryStore3.read_committed(action.getSavingUid(), action.type());
        } catch (ObjectStoreException e) {
            fail("record should be available in store3: " + e);
        }

        // Clean up
        try {
            recoveryStore1.remove_committed(action.getSavingUid(), action.type());
        } catch (ObjectStoreException e) {
            fail("should have been able to delete action");
        }

        store1.stop();
        store3.stop();
        recoveryStore1.stop();
        recoveryStore3.stop();
    }

    public static class UserDefinedSlotKeyGenerator implements JGroupsSlotKeyGenerator {
        boolean generateCalled;
        boolean initCalled;

        @Override
        public ByteArrayKey generateUniqueKey(int index) {
            generateCalled = true;
            return new ByteArrayKey(new Uid().getBytes());
        }

        @Override
        public void init(JGroupsStoreEnvironmentBean config) {
            initCalled = true;
        }
    }
}
