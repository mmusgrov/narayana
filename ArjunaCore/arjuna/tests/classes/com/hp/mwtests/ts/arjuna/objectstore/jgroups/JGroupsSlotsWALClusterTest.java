/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsSlots;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsStoreEnvironmentBean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests cluster-wide failure recovery with WAL.
 * Simulates all 3 nodes in a cluster failing and recovering from WAL.
 */
public class JGroupsSlotsWALClusterTest {

    private static final String CLUSTER_NAME = "wal-cluster-test-" + System.currentTimeMillis();
    private static final String STORE_DIR = System.getProperty("user.dir") + "/target/jgroups-wal-cluster-test";
    private static final int NUM_SLOTS = 256;

    private final List<SlotNode> nodes = new ArrayList<>();

    /**
     * Represents a cluster node with WAL enabled
     */
    private static class SlotNode {
        final String name;
        final JGroupsStoreEnvironmentBean config;
        JGroupsSlots slots;
        final String storeDir;

        SlotNode(String name, String clusterName, String baseDir) throws Exception {
            this.name = name;
            this.storeDir = baseDir + "/" + name;

            this.config = new JGroupsStoreEnvironmentBean();
            config.setJGroupsConfigFileName("jgroups.xml");
            config.setNodeAddress(name);
            config.setGroupName(clusterName);
            config.setNumberOfSlots(NUM_SLOTS);
            config.setStoreDir(storeDir);

            // CRITICAL: Use SharedSlotKeyGenerator for cluster
            config.setSlotKeyGeneratorClassName(SharedSlotKeyGenerator.class.getName());

            // Enable WAL
            config.setWalEnabled(true);
            config.setWalSyncWrites(false);  // Faster for tests
            config.setWalSyncDeletes(false);
        }

        void start() throws IOException {
            slots = new JGroupsSlots();
            slots.init(config);
        }

        void stop() {
            try {
                if (slots != null) {
                    slots.shutdown();
                }
            } catch (Exception e) {
                System.err.println("Error stopping " + name + ": " + e.getMessage());
            }
        }

    }

    @Before
    public void setUp() throws Exception {
        System.out.println("\n=== Setting up WAL cluster test ===");
        cleanupStoreDir();
    }

    @After
    public void tearDown() {
        System.out.println("\n=== Tearing down WAL cluster test ===");

        for (SlotNode node : nodes) {
            node.stop();
        }
        nodes.clear();

        cleanupStoreDir();
    }

    private void cleanupStoreDir() {
        try {
            Path storePath = Paths.get(STORE_DIR);
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
        } catch (IOException e) {
            // Ignore
        }
    }

    /**
     * Test cluster-wide failure recovery:
     * 1. Start 3-node cluster
     * 2. Write data
     * 3. Stop ALL nodes (cluster-wide failure)
     * 4. Restart ALL nodes
     * 5. Verify data recovered from WAL
     */
    @Test
    public void testClusterWideFailureRecovery() throws Exception {
        System.out.println("Testing cluster-wide failure recovery with WAL");

        // ===== Phase 1: Create cluster and write data =====
        System.out.println("\n--- Phase 1: Creating 3-node cluster ---");
        createCluster(3);

        // Write data from node1
        int slotId = 42;
        byte[] data = "cluster-recovery-test-data".getBytes();

        System.out.println("Writing data to slot " + slotId + " from node1");
        nodes.get(0).slots.write(slotId, data, true);

        // Wait for replication
        Thread.sleep(500);

        // Verify all nodes can read the data (from cache)
        for (int i = 0; i < nodes.size(); i++) {
            byte[] result = nodes.get(i).slots.read(slotId);
            assertArrayEquals("Node" + (char)('A' + i) + " should have data", data, result);
        }
        System.out.println("✓ All 3 nodes have the data in cache");

        // ===== Phase 2: Simulate cluster-wide failure =====
        System.out.println("\n--- Phase 2: Simulating cluster-wide failure ---");
        for (SlotNode node : nodes) {
            node.stop();
            System.out.println(node.name + " stopped");
        }
        nodes.clear();
        System.out.println("✓ All nodes stopped (cluster-wide failure)");

        // ===== Phase 3: Restart cluster =====
        System.out.println("\n--- Phase 3: Restarting cluster from WAL ---");
        createCluster(3);

        // Verify data recovered from WAL
        for (int i = 0; i < nodes.size(); i++) {
            byte[] result = nodes.get(i).slots.read(slotId);
            assertNotNull("Node" + (char)('A' + i) + " should recover data from WAL", result);
            assertArrayEquals("Node" + (char)('A' + i) + " data should match", data, result);
        }

        System.out.println("✓ All 3 nodes recovered data from WAL!");
        System.out.println("✓ Cluster-wide failure recovery successful");
    }

    /**
     * Test that without WAL, cluster-wide failure loses data
     */
    @Test
    public void testClusterWideFailureWithoutWAL() throws Exception {
        System.out.println("Testing cluster-wide failure WITHOUT WAL (data loss expected)");

        // Create cluster WITHOUT WAL
        for (int i = 0; i < 3; i++) {
            String nodeName = "Node" + (char)('A' + i);
            SlotNode node = new SlotNode(nodeName, CLUSTER_NAME, STORE_DIR);
            node.config.setWalEnabled(false);  // Disable WAL
            nodes.add(node);
        }

        for (int i = 0; i < 3; i++) {
            nodes.get(i).start();
        }
        Thread.sleep(3000);  // Wait for cluster formation

        // Write data
        int slotId = 50;
        byte[] data = "will-be-lost".getBytes();
        nodes.get(0).slots.write(slotId, data, true);
        Thread.sleep(500);

        // Stop all nodes
        for (SlotNode node : nodes) {
            node.stop();
        }
        nodes.clear();

        // Restart cluster
        for (int i = 0; i < 3; i++) {
            String nodeName = "Node" + (char)('A' + i);
            SlotNode node = new SlotNode(nodeName, CLUSTER_NAME, STORE_DIR);
            node.config.setWalEnabled(false);
            node.start();
            nodes.add(node);
        }

        // Verify data is LOST
        byte[] result = nodes.get(0).slots.read(slotId);
        assertNull("Without WAL, data should be lost after cluster-wide failure", result);

        System.out.println("✓ Confirmed: without WAL, cluster-wide failure loses data");
    }

    /**
     * Test partial cluster failure (1 node fails, 2 survive)
     * Should recover without WAL since data is replicated
     */
    @Test
    public void testPartialClusterFailure() throws Exception {
        System.out.println("Testing partial cluster failure (1 node fails, 2 survive)");

        createCluster(3);

        // Write data
        int slotId = 60;
        byte[] data = "replicated-data".getBytes();
        nodes.get(0).slots.write(slotId, data, true);
        Thread.sleep(500);

        // Stop node 3 only (partial failure)
        System.out.println("Stopping NodeC (partial failure)");
        nodes.get(2).stop();

        // Nodes 1 and 2 should still have the data (from replication)
        assertArrayEquals("Node1 still has data", data, nodes.get(0).slots.read(slotId));
        assertArrayEquals("Node2 still has data", data, nodes.get(1).slots.read(slotId));

        // Restart node 3
        System.out.println("Restarting NodeC");
        nodes.get(2).config.setCache(null);  // Force new cache
        nodes.get(2).start();

        // Wait for rejoin
        Thread.sleep(1000);

        // Node 3 should get data from OTHER nodes (via replication)
        // OR from its own WAL
        byte[] result = nodes.get(2).slots.read(slotId);
        assertNotNull("NodeC should recover data (from replication or WAL)", result);
        assertArrayEquals("NodeC data should match", data, result);

        System.out.println("✓ Partial failure recovery successful (replication + WAL)");
    }

    /**
     * Create and start cluster nodes
     */
    private void createCluster(int numNodes) throws Exception {
        for (int i = 0; i < numNodes; i++) {
            String nodeName = "Node" + (char)('A' + i);
            SlotNode node = new SlotNode(nodeName, CLUSTER_NAME, STORE_DIR);
            nodes.add(node);
        }

        for (int i = 0; i < numNodes; i++) {
            nodes.get(i).start();
        }

        // Wait for cluster formation and replication
        Thread.sleep(3000);

        System.out.println("Cluster formed with " + numNodes + " nodes");
    }
}
