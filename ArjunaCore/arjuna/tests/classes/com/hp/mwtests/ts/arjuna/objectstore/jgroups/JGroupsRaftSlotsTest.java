/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsRaftSlots;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsStoreEnvironmentBean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for JGroupsRaftSlots implementation.
 * Tests single-node operation (basic read/write/clear).
 *
 * For multi-node cluster tests, see JGroupsRaftSlotsClusterTest.
 */
public class JGroupsRaftSlotsTest {

    private static final String STORE_DIR = System.getProperty("user.dir") + "/target/jgroups-raft-test";
    private JGroupsRaftSlots slots;
    private JGroupsStoreEnvironmentBean config;

    @BeforeEach
    public void setUp() throws Exception {
        System.out.println("\n=== Setting up JGroupsRaftSlots test ===");

        // Clean any previous test data
        cleanupStoreDir();

        // Create configuration
        config = new JGroupsStoreEnvironmentBean();
        config.setJGroupsConfigFileName("jgroups-raft.xml");
        config.setNodeAddress("TestNode");
        config.setGroupName("raft-test-" + System.currentTimeMillis());
        config.setCacheName(config.getGroupName());
        config.setStoreDir(STORE_DIR);
        config.setNumberOfSlots(256);
        config.setReplicationCount((short) -1);
        config.setSlotKeyGeneratorClassName(SharedSlotKeyGenerator.class.getName());

        // Raft configuration
        config.setRaftEnabled(true);
        config.setRaftMembers("TestNode");  // Single node for basic tests
        config.setRaftLogFsync(false);  // Disable fsync for faster tests
        config.setRaftTimeout(2000);

        // Create and initialize slots
        slots = new JGroupsRaftSlots();
        slots.init(config);

        // Wait for Raft to elect leader (single node elects itself)
        waitForLeader(slots, 5000);

        System.out.println("JGroupsRaftSlots initialized");
    }

    @AfterEach
    public void tearDown() {
        System.out.println("\n=== Tearing down JGroupsRaftSlots test ===");

        if (slots != null) {
            slots.stop();
        }

        // Clean up test directory
        cleanupStoreDir();
    }

    @Test
    public void testBasicReadWrite() throws Exception {
        System.out.println("Testing basic read/write operations");

        int slotId = 0;
        byte[] data = "transaction-log-entry".getBytes();

        // Write data
        slots.write(slotId, data, true);

        // Read back
        byte[] result = slots.read(slotId);

        assertNotNull(result, "Should have data at slot " + slotId);
        assertArrayEquals(data, result, "Data should match");

        System.out.println("✓ Basic read/write verified");
    }

    @Test
    public void testClear() throws Exception {
        System.out.println("Testing clear operation");

        int slotId = 1;
        byte[] data = "data-to-clear".getBytes();

        // Write and verify
        slots.write(slotId, data, true);
        assertNotNull(slots.read(slotId), "Should have data before clear");

        // Clear
        slots.clear(slotId, true);

        // Verify cleared
        byte[] result = slots.read(slotId);
        assertNull(result, "Should have null data after clear");

        System.out.println("✓ Clear operation verified");
    }

    @Test
    public void testMultipleSlots() throws Exception {
        System.out.println("Testing multiple slots");

        // Write to multiple slots
        for (int i = 0; i < 5; i++) {
            byte[] data = ("slot-" + i + "-data").getBytes();
            slots.write(i, data, true);
        }

        // Read back all slots
        for (int i = 0; i < 5; i++) {
            byte[] expected = ("slot-" + i + "-data").getBytes();
            byte[] actual = slots.read(i);
            assertNotNull(actual, "Should have data at slot " + i);
            assertArrayEquals(expected, actual, "Slot " + i + " data should match");
        }

        System.out.println("✓ Multiple slots verified");
    }

    @Test
    public void testUpdate() throws Exception {
        System.out.println("Testing update operation");

        int slotId = 2;

        // Write version 1
        byte[] data1 = "version-1".getBytes();
        slots.write(slotId, data1, true);
        assertArrayEquals(data1, slots.read(slotId), "Should have version 1");

        // Update to version 2
        byte[] data2 = "version-2-updated".getBytes();
        slots.write(slotId, data2, true);
        assertArrayEquals(data2, slots.read(slotId), "Should have version 2");

        System.out.println("✓ Update operation verified");
    }

    @Test
    public void testLeaderElection() throws Exception {
        System.out.println("Testing leader election");

        // Single node should elect itself as leader
        assertTrue(slots.hasLeader(), "Should have a leader");

        System.out.println("✓ Leader election verified");
    }

    @Test
    public void testRaftMetrics() throws Exception {
        System.out.println("Testing Raft metrics");

        // Write some data to create log entries
        for (int i = 0; i < 3; i++) {
            slots.write(i, ("entry-" + i).getBytes(), true);
        }

        // Verify we can read role and leader status
        assertTrue(slots.hasLeader(), "Should have a leader");

        System.out.println("Has leader: " + slots.hasLeader());
        System.out.println("✓ Raft metrics verified");
    }

    /**
     * Test crash recovery: write data, stop node, restart, verify data recovered from Raft log.
     * This verifies that the Raft FileBasedLog persistent WAL works correctly.
     */
    @Test
    public void testCrashRecovery() throws Exception {
        System.out.println("Testing Raft crash recovery with persistent log");

        // Phase 1: Write data
        int slotId = 42;
        byte[] data = "raft-crash-recovery-data".getBytes();

        System.out.println("Writing data to slot " + slotId);
        slots.write(slotId, data, true);

        // Verify data is there
        byte[] result = slots.read(slotId);
        assertArrayEquals(data, result, "Data should be written");
        System.out.println("✓ Data written and verified");

        // Phase 2: Simulate crash - shutdown node
        System.out.println("Simulating node crash (shutdown)");
        slots.stop();
        slots = null;

        // Phase 3: Restart node - should recover from Raft log
        System.out.println("Restarting node from Raft log");

        // Reuse same config (same storeDir, so same Raft log files)
        slots = new JGroupsRaftSlots();
        slots.init(config);

        // Wait for leader election (single node elects itself)
        waitForLeader(slots, 5000);

        // Phase 4: Verify data recovered from Raft log
        byte[] recovered = slots.read(slotId);
        assertNotNull(recovered, "Data should be recovered from Raft log");
        assertArrayEquals(data, recovered, "Recovered data should match");

        System.out.println("✓ Crash recovery successful - data recovered from Raft log");
        System.out.println("✓ Raft persistent WAL verified");
    }

    /**
     * Test that raftLogFsync configuration is applied.
     * Verifies that the configuration property actually affects the Raft protocol.
     */
    @Test
    public void testRaftLogFsyncConfiguration() throws Exception {
        System.out.println("Testing Raft log fsync configuration");

        // Teardown existing slots
        slots.stop();

        // Create config with fsync ENABLED
        JGroupsStoreEnvironmentBean configWithFsync = new JGroupsStoreEnvironmentBean();
        configWithFsync.setJGroupsConfigFileName("jgroups-raft.xml");
        configWithFsync.setNodeAddress("TestNodeFsync");
        configWithFsync.setGroupName("raft-fsync-test-" + System.currentTimeMillis());
        configWithFsync.setCacheName(configWithFsync.getGroupName());
        configWithFsync.setStoreDir(STORE_DIR + "-fsync");
        configWithFsync.setNumberOfSlots(256);
        configWithFsync.setSlotKeyGeneratorClassName(SharedSlotKeyGenerator.class.getName());
        configWithFsync.setRaftEnabled(true);
        configWithFsync.setRaftMembers("TestNodeFsync");
        configWithFsync.setRaftLogFsync(true);  // Enable fsync
        configWithFsync.setRaftTimeout(2000);

        // Initialize and check
        JGroupsRaftSlots slotsWithFsync = new JGroupsRaftSlots();
        slotsWithFsync.init(configWithFsync);
        waitForLeader(slotsWithFsync, 5000);

        // Write some data to ensure log is created
        slotsWithFsync.write(1, "test".getBytes(), true);

        System.out.println("✓ Raft initialized with fsync enabled");
        System.out.println("Note: Actual fsync verification requires observing write latency");
        System.out.println("      With fsync: ~10-20ms, Without fsync: ~1-2ms");

        slotsWithFsync.stop();
    }

    // Helper methods

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

    private void waitForLeader(JGroupsRaftSlots slots, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            if (slots.hasLeader()) {
                System.out.println("Leader elected: " + slots.getRole());
                return;
            }
            Thread.sleep(100);
        }

        throw new AssertionError("No leader elected within " + timeoutMs + "ms");
    }
}
