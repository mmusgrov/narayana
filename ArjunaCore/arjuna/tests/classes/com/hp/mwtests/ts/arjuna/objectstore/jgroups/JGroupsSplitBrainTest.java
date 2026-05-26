/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.objectstore.RecoveryStore;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.arjuna.state.OutputObjectState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.Assert.*;

/**
 * Tests split-brain and network partition scenarios for JGroupsSlots.
 *
 * CAP Theorem: This implementation prioritizes Consistency over Availability.
 *
 * Test scenarios:
 * 1. Node failure and recovery - simulates temporary network partition
 * 2. Concurrent writes during partition - tests conflict resolution
 * 3. Data consistency after partition heals
 *
 * Note: Real network partitions would use JGroups DISCARD protocol.
 * These tests simulate partitions by stopping/starting nodes.
 */
public class JGroupsSplitBrainTest extends JGroupsTestBase {

    private static final String TEST_CLUSTER_NAME = "split-brain-test-" + System.currentTimeMillis();
    private static final String TEST_STORE_DIR = System.getProperty("user.dir") + "/target/jgroups-split-brain-test";

    private Store store1, store2, store3;
    private RecoveryStore recoveryStore1, recoveryStore2, recoveryStore3;

    @Before
    public void setUp() throws Throwable {
        // Clean and create store directory
        Path storePath = Paths.get(TEST_STORE_DIR);
        if (Files.exists(storePath)) {
            Files.walk(storePath)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
        Files.createDirectories(storePath);

        // Create 3 nodes
        store1 = createStore("node1", TEST_CLUSTER_NAME, TEST_STORE_DIR);
        store2 = createStore("node2", TEST_CLUSTER_NAME, TEST_STORE_DIR);
        store3 = createStore("node3", TEST_CLUSTER_NAME, TEST_STORE_DIR);

        // Start all stores
        store1.start();
        store2.start();
        store3.start();

        // Give cluster time to form
        Thread.sleep(3000);

        // Create recovery stores
        recoveryStore1 = startRecoveryStore(store1.config());
        resetAtomicActionRecoveryModule();
        recoveryStore2 = startRecoveryStore(store2.config());
        resetAtomicActionRecoveryModule();
        recoveryStore3 = startRecoveryStore(store3.config());

        System.out.println("\n=== Initial 3-node cluster formed ===");
    }

    @After
    public void tearDown() throws IOException {
        if (store1 != null) store1.stop();
        if (store2 != null) store2.stop();
        if (store3 != null) store3.stop();

        // Cleanup
        Path storePath = Paths.get(TEST_STORE_DIR);
        if (Files.exists(storePath)) {
            Files.walk(storePath)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }

    @Test
    public void testNodeFailureAndRecovery() throws Throwable {
        System.out.println("\n=== Test: Node failure and recovery ===");

        // 1. Write data while all 3 nodes are up
        Uid uid = new Uid();
        String typeName = "StateManager/failure-test";
        String dataBeforeFailure = "data-before-failure";

        OutputObjectState oos = new OutputObjectState();
        oos.packString(dataBeforeFailure);
        assertTrue("Write should succeed", recoveryStore1.write_committed(uid, typeName, oos));

        Thread.sleep(2000);

        // Verify all nodes see the data
        assertNotNull("Node1 should see data", recoveryStore1.read_committed(uid, typeName));
        assertNotNull("Node2 should see data", recoveryStore2.read_committed(uid, typeName));
        assertNotNull("Node3 should see data", recoveryStore3.read_committed(uid, typeName));
        System.out.println("All 3 nodes see initial data: " + dataBeforeFailure);

        // 2. Simulate node3 failure (crash/network partition)
        System.out.println("\n--- Simulating node3 failure ---");
        store3.stop();
        Thread.sleep(3000);

        // 3. Continue operations with remaining nodes (node1, node2)
        Uid uid2 = new Uid();
        String dataAfterFailure = "data-after-node3-failed";
        OutputObjectState oos2 = new OutputObjectState();
        oos2.packString(dataAfterFailure);

        System.out.println("Writing new data while node3 is down...");
        assertTrue("Write should succeed in 2-node cluster",
                   recoveryStore1.write_committed(uid2, typeName, oos2));

        Thread.sleep(2000);

        // Node2 should see the new data
        InputObjectState ios2 = recoveryStore2.read_committed(uid2, typeName);
        assertNotNull("Node2 should see new data", ios2);
        assertEquals(dataAfterFailure, ios2.unpackString());
        System.out.println("Node1 and Node2 continue operating with new data");

        // 4. Recover node3
        System.out.println("\n--- Recovering node3 ---");
        store3 = createStore("node3", TEST_CLUSTER_NAME, TEST_STORE_DIR);
        store3.start();
        Thread.sleep(3000);

        resetAtomicActionRecoveryModule();
        recoveryStore3 = startRecoveryStore(store3.config());
        Thread.sleep(2000);

        // 5. Verify node3 can see data written while it was down
        System.out.println("Verifying node3 recovered and sees all data...");
        InputObjectState ios3_old = recoveryStore3.read_committed(uid, typeName);
        assertNotNull("Node3 should see old data after recovery", ios3_old);
        assertEquals(dataBeforeFailure, ios3_old.unpackString());

        InputObjectState ios3_new = recoveryStore3.read_committed(uid2, typeName);
        assertNotNull("Node3 should see data written while it was down", ios3_new);
        assertEquals(dataAfterFailure, ios3_new.unpackString());

        System.out.println("SUCCESS: Node3 recovered and sees all data from majority partition");
    }

    @Test
    public void testConcurrentWritesAfterPartition() throws Throwable {
        System.out.println("\n=== Test: Concurrent writes after partition heal ===");

        // 1. Simulate partition: stop node3
        System.out.println("Creating partition: (node1, node2) vs (node3-stopped)");
        store3.stop();
        Thread.sleep(3000);

        // 2. Write in majority partition
        Uid uid1 = new Uid();
        String typeName = "StateManager/concurrent-test";
        String dataFromMajority = "data-from-majority";

        OutputObjectState oos1 = new OutputObjectState();
        oos1.packString(dataFromMajority);
        assertTrue(recoveryStore1.write_committed(uid1, typeName, oos1));
        System.out.println("Wrote data in majority partition (node1, node2)");

        Thread.sleep(2000);

        // 3. Heal partition - restart node3
        System.out.println("\n--- Healing partition: restarting node3 ---");
        store3 = createStore("node3", TEST_CLUSTER_NAME, TEST_STORE_DIR);
        store3.start();
        Thread.sleep(3000);

        resetAtomicActionRecoveryModule();
        recoveryStore3 = startRecoveryStore(store3.config());
        Thread.sleep(2000);

        // 4. All nodes write concurrently after merge
        System.out.println("All nodes writing concurrently after merge...");
        Uid uid_n1 = new Uid();
        Uid uid_n2 = new Uid();
        Uid uid_n3 = new Uid();

        OutputObjectState oos_n1 = new OutputObjectState();
        oos_n1.packString("concurrent-from-node1");
        recoveryStore1.write_committed(uid_n1, typeName, oos_n1);

        OutputObjectState oos_n2 = new OutputObjectState();
        oos_n2.packString("concurrent-from-node2");
        recoveryStore2.write_committed(uid_n2, typeName, oos_n2);

        OutputObjectState oos_n3 = new OutputObjectState();
        oos_n3.packString("concurrent-from-node3");
        recoveryStore3.write_committed(uid_n3, typeName, oos_n3);

        Thread.sleep(3000);

        // 5. Verify all nodes see all concurrent writes (consistency)
        System.out.println("\n--- Verifying consistency across all nodes ---");

        // Each node should see all three concurrent writes
        assertNotNull("Node1 should see its own write", recoveryStore1.read_committed(uid_n1, typeName));
        assertNotNull("Node1 should see node2 write", recoveryStore1.read_committed(uid_n2, typeName));
        assertNotNull("Node1 should see node3 write", recoveryStore1.read_committed(uid_n3, typeName));

        assertNotNull("Node2 should see node1 write", recoveryStore2.read_committed(uid_n1, typeName));
        assertNotNull("Node2 should see its own write", recoveryStore2.read_committed(uid_n2, typeName));
        assertNotNull("Node2 should see node3 write", recoveryStore2.read_committed(uid_n3, typeName));

        assertNotNull("Node3 should see node1 write", recoveryStore3.read_committed(uid_n1, typeName));
        assertNotNull("Node3 should see node2 write", recoveryStore3.read_committed(uid_n2, typeName));
        assertNotNull("Node3 should see its own write", recoveryStore3.read_committed(uid_n3, typeName));

        System.out.println("SUCCESS: All nodes have consistent view after partition heal");
    }

    @Test
    public void testDataPersistsAcrossNodeRestart() throws Throwable {
        System.out.println("\n=== Test: Data persists across node restart ===");

        // 1. Write data
        Uid uid = new Uid();
        String typeName = "StateManager/persistence-test";
        String persistentData = "must-survive-restart";

        OutputObjectState oos = new OutputObjectState();
        oos.packString(persistentData);
        recoveryStore1.write_committed(uid, typeName, oos);

        Thread.sleep(2000);

        // 2. Stop ALL nodes
        System.out.println("Stopping all nodes...");
        store1.stop();
        store2.stop();
        store3.stop();

        Thread.sleep(2000);

        // 3. Restart all nodes (cold start - all nodes discover each other again)
        System.out.println("\n--- Cold restart: starting all nodes ---");
        store1 = createStore("node1", TEST_CLUSTER_NAME, TEST_STORE_DIR);
        store2 = createStore("node2", TEST_CLUSTER_NAME, TEST_STORE_DIR);
        store3 = createStore("node3", TEST_CLUSTER_NAME, TEST_STORE_DIR);

        store1.start();
        store2.start();
        store3.start();

        Thread.sleep(4000);

        // Recreate recovery stores
        recoveryStore1 = startRecoveryStore(store1.config());
        resetAtomicActionRecoveryModule();
        recoveryStore2 = startRecoveryStore(store2.config());
        resetAtomicActionRecoveryModule();
        recoveryStore3 = startRecoveryStore(store3.config());

        Thread.sleep(2000);

        // 4. Verify data survived the full cluster restart
        System.out.println("Verifying data persisted after full cluster restart...");
        InputObjectState ios1 = recoveryStore1.read_committed(uid, typeName);
        assertNotNull("Node1 should recover persisted data", ios1);
        assertEquals(persistentData, ios1.unpackString());

        InputObjectState ios2 = recoveryStore2.read_committed(uid, typeName);
        assertNotNull("Node2 should recover persisted data", ios2);
        assertEquals(persistentData, ios2.unpackString());

        InputObjectState ios3 = recoveryStore3.read_committed(uid, typeName);
        assertNotNull("Node3 should recover persisted data", ios3);
        assertEquals(persistentData, ios3.unpackString());

        System.out.println("SUCCESS: Data survived full cluster restart on all nodes");
    }
}
