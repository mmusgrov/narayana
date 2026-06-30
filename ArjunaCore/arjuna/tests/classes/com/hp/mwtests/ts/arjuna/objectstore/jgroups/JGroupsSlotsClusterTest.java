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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
        Thread.sleep(500); // Allow view to propagate
        for (SlotNode node : nodes) {
            assertEquals(numNodes, node.getClusterSize(), node.name + " should see all nodes");
        }

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

        // Allow replication
        Thread.sleep(1000);

        // Read from node2
        byte[] result2 = nodes.get(1).slots.read(slotId);
        assertNotNull(result2, "Node2 should have data at slot " + slotId);
        assertArrayEquals(data, result2, "Node2 data should match");

        // Read from node3
        byte[] result3 = nodes.get(2).slots.read(slotId);
        assertNotNull(result3, "Node3 should have data at slot " + slotId);
        assertArrayEquals(data, result3, "Node3 data should match");

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

        // Allow replication
        Thread.sleep(1000);

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
        Thread.sleep(1000);
        assertNotNull(nodes.get(1).slots.read(slotId), "Node2 should have data before clear");

        // Clear from node1
        nodes.get(0).slots.clear(slotId, true);
        Thread.sleep(1000);

        // Verify cleared on node2
        byte[] result = nodes.get(1).slots.read(slotId);
        assertNull(result, "Node2 should have null data after clear");

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

        // Allow replication
        Thread.sleep(1000);

        // Verify each node can read all slots
        for (int readerIdx = 0; readerIdx < nodes.size(); readerIdx++) {
            for (int writerIdx = 0; writerIdx < nodes.size(); writerIdx++) {
                int slotId = writerIdx * 10;
                byte[] expected = ("node" + (writerIdx+1) + "-data").getBytes();
                byte[] actual = nodes.get(readerIdx).slots.read(slotId);

                assertNotNull(actual, "Node" + (readerIdx+1) + " should see slot " + slotId +
                        " from Node" + (writerIdx+1));
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
        Thread.sleep(500);

        byte[] read1 = nodes.get(1).slots.read(slotId);
        assertArrayEquals(data1, read1, "Node2 should have version 1");

        // Update the slot
        byte[] data2 = "version-2-updated".getBytes();
        nodes.get(0).slots.write(slotId, data2, true);
        Thread.sleep(500);

        // Verify update replicated
        byte[] read2 = nodes.get(1).slots.read(slotId);
        assertArrayEquals(data2, read2, "Node2 should have version 2");

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
        Thread.sleep(500);

        // Add a third node
        System.out.println("Adding Node3 to existing cluster...");
        SlotNode node3 = new SlotNode("Node3", CLUSTER_NAME, STORE_DIR);

        ClusterFormationListener listener = new ClusterFormationListener(3);
        nodes.get(0).config.getCache().addReceiver(listener);

        node3.start();
        nodes.add(node3);

        assertTrue(listener.await(10, TimeUnit.SECONDS), "Cluster should grow to 3 nodes");
        Thread.sleep(1000); // Allow data migration

        // Verify node3 can see the existing data
        byte[] result = node3.slots.read(slotId);
        assertNotNull(result, "Node3 should see existing data");
        assertArrayEquals(data, result, "Node3 data should match");

        System.out.println("✓ New node joining cluster verified");
    }
}
