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
                // Stop the cache to disconnect from JGroups cluster
                // Without this, the cache remains connected and causes cache reuse issues
                // when restarting nodes with the same cluster name
                if (config != null) {
                    try {
                        config.getCache().stop();
                    } catch (Exception e) {
                        // Ignore - cache might not have been started
                    }
                    // Clear cache reference so next start creates a new one
                    config.setCache(null);
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
     * 2. Write data from one node
     * 3. Stop ALL nodes (cluster-wide failure)
     * 4. Restart ALL nodes
     * 5. Verify the node that wrote the data recovers it from WAL
     *
     * Note: WAL is per-node. Only the node that wrote the data has it in WAL.
     * After restart, that node recovers from WAL, then normal cache replication
     * distributes the data to other nodes.
     */
    @Test
    public void testClusterWideFailureRecovery() throws Exception {
        System.out.println("Testing cluster-wide failure recovery with WAL");

        // ===== Phase 1: Create cluster and write data =====
        System.out.println("\n--- Phase 1: Creating 3-node cluster ---");
        createCluster(3);

        // Write data from NodeA only
        int slotId = 42;
        byte[] data = "cluster-recovery-data".getBytes();

        System.out.println("NodeA writing data to slot " + slotId);
        nodes.get(0).slots.write(slotId, data, true);

        // Wait for replication to all nodes
        Thread.sleep(1000);

        // Verify all nodes have the data (via cache replication)
        for (int i = 0; i < nodes.size(); i++) {
            byte[] result = nodes.get(i).slots.read(slotId);
            assertArrayEquals("Node" + (char)('A' + i) + " should have data before failure",
                data, result);
        }
        System.out.println("✓ All nodes have data (replicated via cache)");

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

        // NodeA should have data from WAL
        // (NodeB and NodeC won't have it because only NodeA wrote it,
        //  so only NodeA has it in WAL. This is expected - WAL is per-node.)
        byte[] resultA = nodes.get(0).slots.read(slotId);
        assertNotNull("NodeA should recover data from its WAL", resultA);
        assertArrayEquals("NodeA data should match", data, resultA);
        System.out.println("✓ NodeA recovered data from WAL");

        System.out.println("✓ Cluster-wide failure recovery successful");
        System.out.println("Note: NodeB and NodeC don't have the data because WAL is per-node.");
        System.out.println("      In production, use replication (multiple nodes writing) for redundancy.");
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
     * Test that WAL doesn't overwrite newer cache data during startup.
     * Scenario: NodeA has stale WAL data, NodeB has newer live data.
     * When NodeA restarts, it should NOT overwrite NodeB's data with its stale WAL.
     */
    @Test
    public void testWALDoesNotOverwriteNewerCacheData() throws Exception {
        System.out.println("Testing WAL doesn't overwrite newer cache data");

        // ===== Phase 1: Create cluster, write v1, stop NodeA =====
        System.out.println("\n--- Phase 1: Write v1 and stop NodeA ---");
        createCluster(2);

        int slotId = 50;
        byte[] dataV1 = "version-1-old".getBytes();
        byte[] dataV2 = "version-2-new".getBytes();

        // NodeA writes v1 (goes to NodeA's WAL and replicates to NodeB)
        nodes.get(0).slots.write(slotId, dataV1, true);
        Thread.sleep(500);

        // Verify both have v1
        assertArrayEquals("NodeA should have v1", dataV1, nodes.get(0).slots.read(slotId));
        assertArrayEquals("NodeB should have v1", dataV1, nodes.get(1).slots.read(slotId));

        // Stop NodeA only (NodeA's WAL still has v1)
        nodes.get(0).stop();
        System.out.println("NodeA stopped (WAL contains v1)");

        // ===== Phase 2: NodeB writes v2 while NodeA is down =====
        System.out.println("\n--- Phase 2: NodeB writes v2 while NodeA is down ---");
        nodes.get(1).slots.write(slotId, dataV2, true);
        Thread.sleep(500);

        // NodeB has v2 in cache and WAL
        assertArrayEquals("NodeB should have v2", dataV2, nodes.get(1).slots.read(slotId));

        // ===== Phase 3: Restart NodeA - should NOT overwrite with stale WAL =====
        System.out.println("\n--- Phase 3: Restart NodeA ---");

        // Restart NodeA
        SlotNode newNodeA = new SlotNode("NodeA", CLUSTER_NAME, STORE_DIR);
        nodes.set(0, newNodeA);
        newNodeA.start();

        // Wait for cluster to form and replicate
        Thread.sleep(2000);

        // NodeA should have v2 from replication, NOT v1 from its stale WAL
        byte[] result = nodes.get(0).slots.read(slotId);
        assertNotNull("NodeA should have data", result);

        // This is the critical assertion: WAL should NOT overwrite newer replicated data
        assertArrayEquals("NodeA should have v2 from replication, not v1 from stale WAL",
            dataV2, result);

        System.out.println("✓ WAL correctly did not overwrite newer cache data");
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
