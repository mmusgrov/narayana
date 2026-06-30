/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsSlots;
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
 * Tests Write-Ahead Log functionality for JGroupsSlots.
 * Verifies crash recovery by simulating stop/start cycles.
 */
public class JGroupsSlotsWALTest {

    private static final String STORE_DIR = System.getProperty("user.dir") + "/target/jgroups-wal-test";
    private static final int NUM_SLOTS = 256;

    private JGroupsStoreEnvironmentBean config;
    private JGroupsSlots slots;

    @BeforeEach
    public void setUp() throws Exception {
        // Clean previous test data
        cleanupStoreDir();

        // Create config with WAL enabled
        config = new JGroupsStoreEnvironmentBean();
        config.setJGroupsConfigFileName("jgroups.xml");
        config.setNodeAddress("test-node");
        // Use unique cluster name per test to avoid cross-contamination
        config.setGroupName("wal-test-" + System.currentTimeMillis());
        config.setNumberOfSlots(NUM_SLOTS);
        config.setStoreDir(STORE_DIR);

        // Enable WAL
        config.setWalEnabled(true);
        config.setWalSyncWrites(true);   // Fsync for durability
        config.setWalSyncDeletes(false);  // No fsync for deletes (faster)

        // Set replication count to 1 (store on this node only) to avoid L1/L2 cache confusion
        config.setReplicationCount((short)1);
    }

    @AfterEach
    public void tearDown() {
        if (slots != null) {
            slots.stop();
        }
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
     * Test basic WAL write and read in same session
     */
    @Test
    public void testBasicWALReadWrite() throws Exception {
        System.out.println("Testing basic WAL read/write operations");

        slots = new JGroupsSlots();
        slots.init(config);

        int slotId = 0;
        byte[] data = "wal-test-data".getBytes();

        // Write data (should go to WAL and cache)
        slots.write(slotId, data, true);

        // Read back (from cache)
        byte[] result = slots.read(slotId);

        assertNotNull(result, "Should have data at slot " + slotId);
        assertArrayEquals(data, result, "Data should match");

        System.out.println("✓ Basic WAL read/write verified");
    }

    /**
     * Test crash recovery: write data, stop, restart, verify data still there
     */
    @Test
    public void testCrashRecovery() throws Exception {
        System.out.println("Testing WAL crash recovery");

        // Session 1: Write data
        slots = new JGroupsSlots();
        slots.init(config);

        int slotId = 42;
        byte[] data = "crash-recovery-test".getBytes();

        slots.write(slotId, data, true);
        System.out.println("Wrote data to slot " + slotId);

        // Verify data is there
        assertArrayEquals(data, slots.read(slotId), "Data written");

        // Simulate crash (stop slots)
        slots.stop();
        config.setCache(null);  // Force new cache (simulate complete restart)
        System.out.println("Simulated crash (stopped slots)");

        // Session 2: Restart and verify data recovered from WAL
        slots = new JGroupsSlots();
        slots.init(config);
        System.out.println("Restarted slots");

        byte[] recovered = slots.read(slotId);
        assertNotNull(recovered, "Data should be recovered from WAL");
        assertArrayEquals(data, recovered, "Recovered data should match");

        System.out.println("✓ Crash recovery verified: data survived restart");
    }

    /**
     * Test multiple slots survive crash
     */
    @Test
    public void testMultipleSlotRecovery() throws Exception {
        System.out.println("Testing multiple slot recovery");

        // Session 1: Write to 10 slots
        slots = new JGroupsSlots();
        slots.init(config);

        for (int i = 0; i < 10; i++) {
            byte[] data = ("slot-" + i + "-data").getBytes();
            slots.write(i, data, true);
        }

        System.out.println("Wrote 10 slots");

        // Stop
        slots.stop();
        config.setCache(null);  // Force new cache

        // Session 2: Restart and verify all 10 slots
        slots = new JGroupsSlots();
        slots.init(config);

        for (int i = 0; i < 10; i++) {
            byte[] expected = ("slot-" + i + "-data").getBytes();
            byte[] actual = slots.read(i);
            assertNotNull(actual, "Slot " + i + " should be recovered");
            assertArrayEquals(expected, actual, "Slot " + i + " data should match");
        }

        System.out.println("✓ All 10 slots recovered successfully");
    }

    /**
     * Test that clear operations are persisted
     */
    @Test
    public void testClearPersistence() throws Exception {
        System.out.println("Testing clear operation persistence");

        // Session 1: Write and clear
        slots = new JGroupsSlots();
        slots.init(config);

        int slotId = 99;
        byte[] data = "data-to-clear".getBytes();

        slots.write(slotId, data, true);
        assertNotNull(slots.read(slotId), "Data written");

        slots.clear(slotId, true);
        assertNull(slots.read(slotId), "Data cleared");

        // Stop and clear cache reference (simulate complete shutdown)
        slots.stop();
        config.setCache(null);  // Force new cache on next init

        // Session 2: Verify clear is persisted
        slots = new JGroupsSlots();
        slots.init(config);

        byte[] result = slots.read(slotId);
        if (result != null) {
            System.err.println("ERROR: Slot " + slotId + " still has data after clear: " + new String(result));
            System.err.println("This means the delete was not persisted to WAL");
        }
        assertNull(result, "Cleared slot should stay cleared after restart");

        System.out.println("✓ Clear operation persistence verified");
    }

    /**
     * Test update operations (write to same slot multiple times)
     */
    @Test
    public void testUpdatePersistence() throws Exception {
        System.out.println("Testing update persistence");

        // Session 1: Write, update, write again
        slots = new JGroupsSlots();
        slots.init(config);

        int slotId = 7;
        slots.write(slotId, "version1".getBytes(), true);
        slots.write(slotId, "version2".getBytes(), true);
        slots.write(slotId, "version3".getBytes(), true);

        // Stop
        slots.stop();
        config.setCache(null);  // Force new cache

        // Session 2: Verify latest version recovered
        slots = new JGroupsSlots();
        slots.init(config);

        byte[] result = slots.read(slotId);
        assertNotNull(result, "Updated data should be recovered");
        assertArrayEquals("version3".getBytes(), result, "Should have latest version");

        System.out.println("✓ Update persistence verified");
    }

    /**
     * Test WAL disabled mode (data not persisted)
     */
    @Test
    public void testWALDisabled() throws Exception {
        System.out.println("Testing WAL disabled (no persistence)");

        // Disable WAL
        config.setWalEnabled(false);

        // Session 1: Write data
        slots = new JGroupsSlots();
        slots.init(config);

        int slotId = 10;
        byte[] data = "non-persistent-data".getBytes();

        slots.write(slotId, data, true);
        assertArrayEquals(data, slots.read(slotId), "Data written");

        // Stop
        slots.stop();
        config.setCache(null);  // Force new cache

        // Session 2: Data should NOT be recovered (no WAL)
        slots = new JGroupsSlots();
        slots.init(config);

        byte[] result = slots.read(slotId);
        assertNull(result, "Data should NOT be recovered when WAL is disabled");

        System.out.println("✓ WAL disabled mode verified (no persistence as expected)");
    }

    /**
     * Test WAL with no fsync (faster but less durable)
     */
    @Test
    public void testWALNoFsync() throws Exception {
        System.out.println("Testing WAL without fsync");

        // Disable fsync for faster writes
        config.setWalSyncWrites(false);

        slots = new JGroupsSlots();
        slots.init(config);

        int slotId = 20;
        byte[] data = "no-fsync-data".getBytes();

        // Write should be faster (no fsync wait)
        long start = System.currentTimeMillis();
        slots.write(slotId, data, true);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("Write latency (no fsync): " + elapsed + "ms");

        // Data still in memory
        assertArrayEquals(data, slots.read(slotId), "Data written");

        // Note: Without fsync, data may be lost on crash before OS flushes buffers
        // This test just verifies the option works, not crash recovery

        System.out.println("✓ WAL without fsync verified");
    }
}
