/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsRaftSlots;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsStoreEnvironmentBean;

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
 * Basic tests for JGroupsRaftSlots implementation.
 * Tests single-node operation (basic read/write/clear).
 *
 * For multi-node cluster tests, see JGroupsRaftSlotsClusterTest.
 */
public class JGroupsRaftSlotsTest {

    private static final String STORE_DIR = System.getProperty("user.dir") + "/target/jgroups-raft-test";
    private JGroupsRaftSlots slots;
    private JGroupsStoreEnvironmentBean config;

    @Before
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

    @After
    public void tearDown() {
        System.out.println("\n=== Tearing down JGroupsRaftSlots test ===");

        if (slots != null) {
            slots.shutdown();
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

        assertNotNull("Should have data at slot " + slotId, result);
        assertArrayEquals("Data should match", data, result);

        System.out.println("✓ Basic read/write verified");
    }

    @Test
    public void testClear() throws Exception {
        System.out.println("Testing clear operation");

        int slotId = 1;
        byte[] data = "data-to-clear".getBytes();

        // Write and verify
        slots.write(slotId, data, true);
        assertNotNull("Should have data before clear", slots.read(slotId));

        // Clear
        slots.clear(slotId, true);

        // Verify cleared
        byte[] result = slots.read(slotId);
        assertNull("Should have null data after clear", result);

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
            assertNotNull("Should have data at slot " + i, actual);
            assertArrayEquals("Slot " + i + " data should match", expected, actual);
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
        assertArrayEquals("Should have version 1", data1, slots.read(slotId));

        // Update to version 2
        byte[] data2 = "version-2-updated".getBytes();
        slots.write(slotId, data2, true);
        assertArrayEquals("Should have version 2", data2, slots.read(slotId));

        System.out.println("✓ Update operation verified");
    }

    @Test
    public void testLeaderElection() throws Exception {
        System.out.println("Testing leader election");

        // Single node should elect itself as leader
        assertTrue("Should have a leader", slots.hasLeader());
// TODO        assertEquals("Should be LEADER", "LEADER", slots.getRole());

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
// TODO        String role = slots.getRole();
// TODO        assertNotNull("Role should not be null", role);
        assertTrue("Should have a leader", slots.hasLeader());

// TODO        System.out.println("Role: " + role);
        System.out.println("Has leader: " + slots.hasLeader());
        System.out.println("✓ Raft metrics verified");
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
