/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsRaftSlots;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsStoreEnvironmentBean;
import org.jgroups.JChannel;
import org.jgroups.Receiver;
import org.jgroups.View;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Multi-node cluster tests for JGroupsRaftSlots implementation.
 * Tests Raft consensus, leader election, and data replication across 3 nodes.
 * <p>
 * This tests the Raft-based implementation at the BackingSlots level with
 * a realistic 3-node cluster configuration.
 */
public class JGroupsRaftSlotsClusterTest {

    private static final String CLUSTER_NAME = "raft-cluster-test-" + System.currentTimeMillis();
    private static final String STORE_DIR = System.getProperty("user.dir") + "/target/jgroups-raft-cluster-test";
    private static final int NUM_SLOTS = 256;

    private final List<SlotNode> nodes = new ArrayList<>();

    /**
     * Represents a Raft cluster node with its own JGroupsRaftSlots instance
     */
    private static class SlotNode {
        final String name;
        final JGroupsStoreEnvironmentBean config;
        final JGroupsRaftSlots slots;
        final String storeDir;

        SlotNode(String name, String clusterName, String members, String baseDir) throws Exception {
            this.name = name;
            this.storeDir = baseDir + "/" + name;

            // Configure the slots
            this.config = new JGroupsStoreEnvironmentBean();
            config.setJGroupsConfigFileName("jgroups-raft.xml");
            config.setNodeAddress(name);
            config.setGroupName(clusterName);
            config.setStoreDir(storeDir);
            config.setCacheName(clusterName);
            config.setNumberOfSlots(NUM_SLOTS);
            config.setReplicationCount((short) -1); // Replicate to all nodes

            // Raft configuration
            config.setRaftEnabled(true);
            config.setRaftMembers(members);
            config.setRaftLogFsync(false);  // Disable fsync for faster tests
            config.setRaftTimeout(5000);

            // CRITICAL: Use SharedSlotKeyGenerator so all nodes use same slot keys
            config.setSlotKeyGeneratorClassName(SharedSlotKeyGenerator.class.getName());

            this.slots = new JGroupsRaftSlots();
        }

        void start() throws IOException {
            slots.init(config);
        }

        void stop() {
            try {
                slots.stop();
            } catch (Exception e) {
                System.err.println("Error stopping " + name + ": " + e.getMessage());
            }
        }

        JChannel getChannel() {
            return slots.getChannel();
        }

        int getClusterSize() {
            try {
                JChannel channel = slots.getChannel();
                return channel != null ? channel.getView().size() : 0;
            } catch (Exception e) {
                return 0;
            }
        }

        boolean hasLeader() {
            return slots.hasLeader();
        }

        String getRole() {
            return slots.getRole();
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

    @Before
    public void setUp() throws Exception {
        System.out.println("\n=== Setting up JGroupsRaftSlots cluster test ===");

        // Clean any previous test data
        cleanupStoreDir();
    }

    @After
    public void tearDown() {
        System.out.println("\n=== Tearing down JGroupsRaftSlots cluster test ===");

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
        // Build members list (e.g., "NodeA,NodeB,NodeC")
        StringBuilder membersBuilder = new StringBuilder();
        for (int i = 0; i < numNodes; i++) {
            if (i > 0) membersBuilder.append(",");
            membersBuilder.append("Node").append((char)('A' + i));
        }
        String members = membersBuilder.toString();

        System.out.println("Creating Raft cluster with members: " + members);

        // Create nodes
        for (int i = 0; i < numNodes; i++) {
            String nodeName = "Node" + (char)('A' + i);
            SlotNode node = new SlotNode(nodeName, CLUSTER_NAME, members, STORE_DIR);
            nodes.add(node);
        }

        // Start nodes and wait for cluster formation
        ClusterFormationListener listener = new ClusterFormationListener(numNodes);

        for (int i = 0; i < numNodes; i++) {
            nodes.get(i).start();

            if (i == 0) {
                // Add listener to first node to track cluster growth
                nodes.get(0).getChannel().setReceiver(listener);
            }
        }

        // Wait for cluster to form
        assertTrue("Cluster should form with " + numNodes + " nodes",
                listener.await(15, TimeUnit.SECONDS));

        // Verify all nodes see the correct cluster size
        Thread.sleep(1000); // Allow view to propagate
        for (SlotNode node : nodes) {
            assertEquals(node.name + " should see all nodes", numNodes, node.getClusterSize());
        }

        System.out.println("Cluster formed with " + numNodes + " nodes");

        // Wait for leader election
        waitForLeader(10000);
    }

    /**
     * Wait for a leader to be elected in the Raft cluster
     */
    private void waitForLeader(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            // Check if any node reports having a leader
            for (SlotNode node : nodes) {
                if (node.hasLeader()) {
                    System.out.println("Leader elected! Roles:");
                    for (SlotNode n : nodes) {
                        System.out.println("  " + n.name + ": " + n.getRole());
                    }
                    return;
                }
            }
            Thread.sleep(200);
        }

        // Print diagnostic info
        System.err.println("No leader elected within timeout. Node states:");
        for (SlotNode node : nodes) {
            System.err.println("  " + node.name + ": hasLeader=" + node.hasLeader() +
                             ", role=" + node.getRole());
        }

        throw new AssertionError("No leader elected within " + timeoutMs + "ms");
    }

    /**
     * Test that multiple JGroupsRaftSlots instances can form a Raft cluster
     */
    @Test
    public void testClusterFormation() throws Exception {
        createCluster(3);

        // Verify cluster size
        for (SlotNode node : nodes) {
            assertEquals(node.name + " should see 3-node cluster", 3, node.getClusterSize());
        }

        // Verify leader elected
        assertTrue("Cluster should have a leader", nodes.stream().anyMatch(SlotNode::hasLeader));

        System.out.println("✓ 3-node Raft cluster formation verified");
    }

    /**
     * Test that data written to one node is replicated to others via Raft consensus
     */
    @Test
    public void testSlotDataReplication() throws Exception {
        createCluster(3);

        int slotId = 0;
        byte[] data = "raft-transaction-log".getBytes();

        // Write to first node (may or may not be leader - Raft handles it)
        nodes.get(0).slots.write(slotId, data, true);

        // Allow Raft consensus and replication
        Thread.sleep(2000);

        // Read from other nodes - should see committed data
        byte[] result2 = nodes.get(1).slots.read(slotId);
        assertNotNull("Node B should have data at slot " + slotId, result2);
        assertArrayEquals("Node B data should match", data, result2);

        byte[] result3 = nodes.get(2).slots.read(slotId);
        assertNotNull("Node C should have data at slot " + slotId, result3);
        assertArrayEquals("Node C data should match", data, result3);

        System.out.println("✓ Slot data replication verified across 3 Raft nodes");
    }

    /**
     * Test writing to multiple different slots and replication
     */
    @Test
    public void testMultipleSlotReplication() throws Exception {
        createCluster(3);

        // Write to different slots from first node
        for (int slot = 0; slot < 5; slot++) {
            byte[] data = ("raft-slot-" + slot + "-data").getBytes();
            nodes.get(0).slots.write(slot, data, true);
            Thread.sleep(500); // Allow each write to commit
        }

        // Allow final replication
        Thread.sleep(1000);

        // Verify all slots on other nodes
        for (int slot = 0; slot < 5; slot++) {
            byte[] expected = ("raft-slot-" + slot + "-data").getBytes();

            byte[] actual2 = nodes.get(1).slots.read(slot);
            assertNotNull("Node B should have data at slot " + slot, actual2);
            assertArrayEquals("Node B slot " + slot + " data should match", expected, actual2);

            byte[] actual3 = nodes.get(2).slots.read(slot);
            assertNotNull("Node C should have data at slot " + slot, actual3);
            assertArrayEquals("Node C slot " + slot + " data should match", expected, actual3);
        }

        System.out.println("✓ Multiple slot replication verified");
    }

    /**
     * Test that clearing a slot is replicated via Raft
     */
    @Test
    public void testSlotClearReplication() throws Exception {
        createCluster(3);

        int slotId = 5;
        byte[] data = "data-to-clear-via-raft".getBytes();

        // Write and verify replication
        nodes.get(0).slots.write(slotId, data, true);
        Thread.sleep(1500);
        assertNotNull("Node B should have data before clear", nodes.get(1).slots.read(slotId));
        assertNotNull("Node C should have data before clear", nodes.get(2).slots.read(slotId));

        // Clear from first node
        nodes.get(0).slots.clear(slotId, true);
        Thread.sleep(1500);

        // Verify cleared on all nodes
        byte[] result2 = nodes.get(1).slots.read(slotId);
        assertNull("Node B should have null data after clear", result2);

        byte[] result3 = nodes.get(2).slots.read(slotId);
        assertNull("Node C should have null data after clear", result3);

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
            byte[] data = ("raft-node" + nodes.get(i).name + "-data").getBytes();
            nodes.get(i).slots.write(slotId, data, true);
        }

        // Allow Raft consensus and replication
        Thread.sleep(3000);

        // Verify each node can read all slots
        for (int readerIdx = 0; readerIdx < nodes.size(); readerIdx++) {
            for (int writerIdx = 0; writerIdx < nodes.size(); writerIdx++) {
                int slotId = writerIdx * 10;
                byte[] expected = ("raft-node" + nodes.get(writerIdx).name + "-data").getBytes();
                byte[] actual = nodes.get(readerIdx).slots.read(slotId);

                assertNotNull(nodes.get(readerIdx).name + " should see slot " + slotId +
                        " from " + nodes.get(writerIdx).name, actual);
                assertArrayEquals(nodes.get(readerIdx).name + " slot " + slotId + " should match",
                        expected, actual);
            }
        }

        System.out.println("✓ Concurrent slot writes from 3 nodes verified");
    }

    /**
     * Test write-update-read cycle with Raft replication
     */
    @Test
    public void testSlotUpdateReplication() throws Exception {
        createCluster(3);

        int slotId = 3;

        // Initial write
        byte[] data1 = "raft-version-1".getBytes();
        nodes.get(0).slots.write(slotId, data1, true);
        Thread.sleep(1000);

        byte[] read1 = nodes.get(1).slots.read(slotId);
        assertArrayEquals("Node B should have version 1", data1, read1);

        // Update the slot
        byte[] data2 = "raft-version-2-updated".getBytes();
        nodes.get(0).slots.write(slotId, data2, true);
        Thread.sleep(1000);

        // Verify update replicated
        byte[] read2 = nodes.get(1).slots.read(slotId);
        assertArrayEquals("Node B should have version 2", data2, read2);

        byte[] read3 = nodes.get(2).slots.read(slotId);
        assertArrayEquals("Node C should have version 2", data2, read3);

        System.out.println("✓ Slot update replication verified");
    }

    /**
     * Test leader election and verify exactly one leader exists
     */
    @Test
    public void testLeaderElection() throws Exception {
        createCluster(3);

        // Count leaders (should be exactly 1)
        int leaderCount = 0;
        String leaderName = null;

        for (SlotNode node : nodes) {
            String role = node.getRole();
            System.out.println(node.name + " role: " + role);

            if ("LEADER".equals(role)) {
                leaderCount++;
                leaderName = node.name;
            }
        }

        assertEquals("Should have exactly 1 leader", 1, leaderCount);
        assertNotNull("Leader should be identified", leaderName);

        System.out.println("✓ Leader election verified: " + leaderName + " is leader");
    }
}
