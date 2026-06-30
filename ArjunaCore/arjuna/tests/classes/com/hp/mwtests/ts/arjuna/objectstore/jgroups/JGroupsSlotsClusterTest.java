/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsSlots;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsStoreEnvironmentBean;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.hp.mwtests.ts.arjuna.objectstore.jgroups.JGroupsTestBase.REPLICATION_TIMEOUT_MS;
import static com.hp.mwtests.ts.arjuna.objectstore.jgroups.JGroupsTestBase.waitFor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests JGroupsSlots clustering - multiple slot stores forming a cluster
 * and replicating data via JGroups.
 * <p>
 * This tests at the BackingSlots implementation level, which is more realistic
 * than testing ReplCache directly.
 */
public class JGroupsSlotsClusterTest {

    private static final String CLUSTER_NAME = "slots-cluster-test-" + System.currentTimeMillis();
    private static final String STORE_DIR = System.getProperty("user.dir") + "/target/jgroups-slots-cluster-test";
    private static final int NUM_SLOTS = 256;

    private final List<SlotNode> nodes = new ArrayList<>();

    /**
     * Represents a cluster node with its own JGroupsSlots instance
     */
    private static class SlotNode {
        final String name;
        final JGroupsStoreEnvironmentBean config;
        final JGroupsSlots slots;
        final String storeDir;

        SlotNode(String name, String clusterName, String baseDir) throws Exception {
            this.name = name;
            this.storeDir = baseDir + "/" + name;

            // Configure the slots
            this.config = new JGroupsStoreEnvironmentBean();
            config.setJGroupsConfigFileName("jgroups.xml");
            config.setNodeAddress(name);
            config.setGroupName(clusterName);
            config.setStoreDir(storeDir);
            config.setCacheName(clusterName);
            config.setNumberOfSlots(NUM_SLOTS);
            config.setReplicationCount((short) -1); // Replicate to all nodes
            // CRITICAL: Use SharedSlotKeyGenerator so all nodes use same slot keys
            config.setSlotKeyGeneratorClassName(SharedSlotKeyGenerator.class.getName());

            this.slots = new JGroupsSlots();
        }

        void start() throws IOException {
            slots.init(config);
        }

        void stop() {
            try {
                if (config.getCache() != null) {
                    config.getCache().stop();
                }
            } catch (Exception e) {
                System.err.println("Error stopping " + name + ": " + e.getMessage());
            }
        }

        int getClusterSize() {
            try {
                return config.getCache().getClusterSize();
            } catch (Exception e) {
                return 0;
            }
        }
    }

    /**
     * View change listener for cluster formation
     */
    private static class ClusterFormationListener implements Receiver {
        private final CountDownLatch latch;
        private final int expectedSize;
        private volatile int currentSize = 0;

        ClusterFormationListener(int expectedSize) {
            this.latch = new CountDownLatch(1);
            this.expectedSize = expectedSize;
        }

        @Override
        public void viewAccepted(View view) {
            currentSize = view.size();
            System.out.println("View change: cluster size = " + currentSize + " (expecting " + expectedSize + ")");
            if (currentSize >= expectedSize) {
                latch.countDown();
            }
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        System.out.println("\n=== Setting up JGroupsSlots cluster test ===");

        // Clean any previous test data
        cleanupStoreDir();
    }

    @AfterEach
    public void tearDown() {
        System.out.println("\n=== Tearing down JGroupsSlots cluster test ===");

        // Stop all nodes
        for (SlotNode node : nodes) {
            node.stop();
        }
        nodes.clear();

        // Clean up directories
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
     * Create and start cluster nodes
     */
    private void createCluster(int numNodes) throws Exception {
        // Create nodes
        for (int i = 0; i < numNodes; i++) {
            String nodeName = "Node" + (i + 1);
            SlotNode node = new SlotNode(nodeName, CLUSTER_NAME, STORE_DIR);
            nodes.add(node);
        }

        // Start nodes and wait for cluster formation
        ClusterFormationListener listener = new ClusterFormationListener(numNodes);

        for (int i = 0; i < numNodes; i++) {
            if (i == 0) {
                // Add listener to first node to track cluster growth
                nodes.get(0).config.getCache().addReceiver(listener);
            }
            nodes.get(i).start();
        }

        // Wait for cluster to form
        assertTrue(listener.await(10, TimeUnit.SECONDS),
                "Cluster should form with " + numNodes + " nodes");

        // Verify all nodes see the correct cluster size
        final int expected = numNodes;
        waitFor(REPLICATION_TIMEOUT_MS, "view propagation to all nodes",
            () -> nodes.stream().allMatch(n -> n.getClusterSize() == expected));

        System.out.println("Cluster formed with " + numNodes + " nodes");
    }

    /**
     * Test that multiple JGroupsSlots instances can form a cluster
     */
    @Test
    public void testClusterFormation() throws Exception {
        createCluster(3);

        for (SlotNode node : nodes) {
            assertEquals(3, node.getClusterSize(), node.name + " should see 3-node cluster");
        }

        System.out.println("✓ 3-node cluster formation verified");
    }

    /**
     * Test that data written to one slot store is replicated to others
     */
    @Test
    public void testSlotDataReplication() throws Exception {
        createCluster(3);

        int slotId = 0;
        byte[] data = "transaction-log-entry-1".getBytes();

        // Write to node1
        nodes.get(0).slots.write(slotId, data, true);

        waitFor(REPLICATION_TIMEOUT_MS, "data replication to Node2",
            () -> Arrays.equals(data, nodes.get(1).slots.read(slotId)));
        waitFor(REPLICATION_TIMEOUT_MS, "data replication to Node3",
            () -> Arrays.equals(data, nodes.get(2).slots.read(slotId)));

        System.out.println("✓ Slot data replication verified across 3 nodes");
    }

    /**
     * Test writing to multiple different slots and replication
     */
    @Test
    public void testMultipleSlotReplication() throws Exception {
        createCluster(2);

        // Write to different slots
        for (int slot = 0; slot < 5; slot++) {
            byte[] data = ("slot-" + slot + "-data").getBytes();
            nodes.get(0).slots.write(slot, data, true);
        }

        // Wait for replication of last slot
        waitFor(REPLICATION_TIMEOUT_MS, "replication of all slots to Node2",
            () -> nodes.get(1).slots.read(4) != null);

        // Verify all slots on node2
        for (int slot = 0; slot < 5; slot++) {
            byte[] expected = ("slot-" + slot + "-data").getBytes();
            byte[] actual = nodes.get(1).slots.read(slot);
            assertNotNull(actual, "Node2 should have data at slot " + slot);
            assertArrayEquals(expected, actual, "Node2 slot " + slot + " data should match");
        }

        System.out.println("✓ Multiple slot replication verified");
    }

    /**
     * Test that clearing a slot is replicated
     */
    @Test
    public void testSlotClearReplication() throws Exception {
        createCluster(2);

        int slotId = 5;
        byte[] data = "data-to-clear".getBytes();

        // Write and verify replication
        nodes.get(0).slots.write(slotId, data, true);
        waitFor(REPLICATION_TIMEOUT_MS, "data replication before clear",
            () -> nodes.get(1).slots.read(slotId) != null);

        // Clear from node1
        nodes.get(0).slots.clear(slotId, true);
        waitFor(REPLICATION_TIMEOUT_MS, "clear replication to Node2",
            () -> nodes.get(1).slots.read(slotId) == null);

        System.out.println("✓ Slot clear replication verified");
    }

    /**
     * Test concurrent writes from different nodes to different slots
     */
    @Test
    public void testConcurrentSlotWrites() throws Exception {
        createCluster(3);

        // Each node writes to a different slot
        for (int i = 0; i < nodes.size(); i++) {
            int slotId = i * 10; // Slots 0, 10, 20
            byte[] data = ("node" + (i+1) + "-data").getBytes();
            nodes.get(i).slots.write(slotId, data, true);
        }

        // Wait for replication of all slots to all nodes
        waitFor(REPLICATION_TIMEOUT_MS, "concurrent write replication", () -> {
            for (int r = 0; r < nodes.size(); r++) {
                for (int w = 0; w < nodes.size(); w++) {
                    if (nodes.get(r).slots.read(w * 10) == null) return false;
                }
            }
            return true;
        });

        // Verify data correctness
        for (int readerIdx = 0; readerIdx < nodes.size(); readerIdx++) {
            for (int writerIdx = 0; writerIdx < nodes.size(); writerIdx++) {
                int slotId = writerIdx * 10;
                byte[] expected = ("node" + (writerIdx+1) + "-data").getBytes();
                byte[] actual = nodes.get(readerIdx).slots.read(slotId);
                assertArrayEquals(expected, actual,
                        "Node" + (readerIdx+1) + " slot " + slotId + " should match");
            }
        }

        System.out.println("✓ Concurrent slot writes from 3 nodes verified");
    }

    /**
     * Test write-update-read cycle with replication
     */
    @Test
    public void testSlotUpdateReplication() throws Exception {
        createCluster(2);

        int slotId = 3;

        // Initial write
        byte[] data1 = "version-1".getBytes();
        nodes.get(0).slots.write(slotId, data1, true);
        waitFor(REPLICATION_TIMEOUT_MS, "v1 replication to Node2",
            () -> Arrays.equals(data1, nodes.get(1).slots.read(slotId)));

        // Update the slot
        byte[] data2 = "version-2-updated".getBytes();
        nodes.get(0).slots.write(slotId, data2, true);
        waitFor(REPLICATION_TIMEOUT_MS, "v2 replication to Node2",
            () -> Arrays.equals(data2, nodes.get(1).slots.read(slotId)));

        System.out.println("✓ Slot update replication verified");
    }

    /**
     * Test that a new node joining the cluster sees existing data
     */
    @Test
    public void testNewNodeJoinsCluster() throws Exception {
        // Start with 2 nodes
        createCluster(2);

        // Write some data
        int slotId = 7;
        byte[] data = "existing-data".getBytes();
        nodes.get(0).slots.write(slotId, data, true);
        waitFor(REPLICATION_TIMEOUT_MS, "data replication before node join",
            () -> nodes.get(1).slots.read(slotId) != null);

        // Add a third node
        System.out.println("Adding Node3 to existing cluster...");
        SlotNode node3 = new SlotNode("Node3", CLUSTER_NAME, STORE_DIR);

        ClusterFormationListener listener = new ClusterFormationListener(3);
        nodes.get(0).config.getCache().addReceiver(listener);

        node3.start();
        nodes.add(node3);

        assertTrue(listener.await(10, TimeUnit.SECONDS), "Cluster should grow to 3 nodes");
        waitFor(REPLICATION_TIMEOUT_MS, "data migration to Node3",
            () -> Arrays.equals(data, node3.slots.read(slotId)));

        System.out.println("✓ New node joining cluster verified");
    }
}
