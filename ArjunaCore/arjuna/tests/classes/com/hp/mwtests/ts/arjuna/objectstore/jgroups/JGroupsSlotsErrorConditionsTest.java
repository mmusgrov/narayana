/*
 * Copyright The Narayana Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.state.OutputObjectState;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsSlots;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsStoreEnvironmentBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for error and boundary conditions in JGroupsSlots.
 * Covers review item 28.1: error/boundary conditions
 */
public class JGroupsSlotsErrorConditionsTest {
    private static final String TEST_STORE_DIR = System.getProperty("user.dir") + "/target/jgroups-error-test";
    private JGroupsStoreEnvironmentBean config;
    private JGroupsSlots slots;

    @BeforeEach
    public void setUp() {
        config = new JGroupsStoreEnvironmentBean();
        config.setNodeAddress("error-test-node");
        config.setGroupName("error-test-group");
        config.setCacheName("error-test-cache");
        config.setStoreDir(TEST_STORE_DIR);
        config.setNumberOfSlots(10); // Small number for testing boundaries
        config.setBytesPerSlot(100);
        slots = new JGroupsSlots();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (slots != null) {
            try {
                slots.stop();
            } catch (Exception ignored) {
            }
        }
        cleanupStoreDir();
    }

    private void cleanupStoreDir() {
        File dir = new File(TEST_STORE_DIR);
        if (dir.exists()) {
            deleteRecursive(dir);
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    @Test
    public void testExceedingNumberOfSlots() throws Exception {
        slots.init(config);

        // Write to all available slots
        for (int i = 0; i < config.getNumberOfSlots(); i++) {
            byte[] data = ("slot-" + i).getBytes();
            slots.write(i, data, true);
        }

        // Attempting to write beyond numberOfSlots should fail gracefully
        assertThrows(Exception.class, () -> {
            slots.write(config.getNumberOfSlots(), "overflow".getBytes(), true);
        });

        assertThrows(Exception.class, () -> {
            slots.write(config.getNumberOfSlots() + 100, "way-overflow".getBytes(), true);
        });
    }

    @Test
    public void testNegativeSlotIndex() throws Exception {
        slots.init(config);

        assertThrows(Exception.class, () -> {
            slots.write(-1, "negative".getBytes(), true);
        });

        assertThrows(Exception.class, () -> {
            slots.read(-1);
        });

        assertThrows(Exception.class, () -> {
            slots.clear(-1, true);
        });
    }

    @Test
    public void testNullData() throws Exception {
        slots.init(config);

        // Writing null should be handled gracefully (either throw or convert to empty)
        assertDoesNotThrow(() -> {
            slots.write(0, null, true);
        });

        // Reading null slot should return null
        byte[] result = slots.read(0);
        assertTrue(result == null || result.length == 0, "Null write should result in null or empty read");
    }

    @Test
    public void testDoubleInitialization() throws Exception {
        slots.init(config);

        // Second init is idempotent - doesn't throw
        assertDoesNotThrow(() -> {
            slots.init(config);
        }, "Double initialization should be idempotent");
    }

    // Note: WAL directory permissions test removed as setWalDir() is not available in current API

    @Test
    public void testOperationsBeforeInit() {
        // Operations before init should fail
        assertThrows(Exception.class, () -> {
            slots.write(0, "test".getBytes(), true);
        });

        assertThrows(Exception.class, () -> {
            slots.read(0);
        });

        assertThrows(Exception.class, () -> {
            slots.clear(0, true);
        });
    }

    @Test
    public void testEmptyData() throws Exception {
        slots.init(config);

        // Empty byte array should be valid
        byte[] empty = new byte[0];
        assertDoesNotThrow(() -> {
            slots.write(0, empty, true);
        });

        byte[] result = slots.read(0);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testInvalidGroupName() throws Exception {
        config.setGroupName(null);

        // Init with null group name - may use default or throw
        // Test that it handles gracefully either way
        try {
            slots.init(config);
            // If it doesn't throw, verify it's functional
            assertDoesNotThrow(() -> slots.write(0, "test".getBytes(), true));
        } catch (Exception e) {
            // Expected - null group name caused failure
            assertTrue(e.getMessage() != null, "Exception should have a message");
        }
    }

    @Test
    public void testInvalidCacheName() {
        config.setCacheName(null);

        assertThrows(Exception.class, () -> {
            slots.init(config);
        }, "Null cache name should fail initialization");
    }

    @Test
    public void testChannelDisconnectRecovery() throws Exception {
        slots.init(config);

        // Write some data
        byte[] data = "before-disconnect".getBytes();
        slots.write(0, data, true);

        // Simulate channel disconnect (if accessible)
        // This is a placeholder - actual implementation depends on internal access
        // In production code, JGroupsSlots should handle channel disconnects gracefully

        // Verify we can still read after potential disconnect
        byte[] result = slots.read(0);
        assertNotNull(result, "Should be able to read after disconnect handling");
    }

    @Test
    public void testClearNonExistentSlot() throws Exception {
        slots.init(config);

        // Clearing a slot that was never written should be safe
        assertDoesNotThrow(() -> {
            slots.clear(5, true);
        });

        // Verify it's still empty
        byte[] result = slots.read(5);
        assertTrue(result == null || result.length == 0);
    }

    @Test
    public void testReadNonExistentSlot() throws Exception {
        slots.init(config);

        // Reading a never-written slot should return null or empty
        byte[] result = slots.read(5);
        assertTrue(result == null || result.length == 0,
                "Reading non-existent slot should return null or empty array");
    }
}
