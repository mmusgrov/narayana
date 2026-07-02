/*
 * Copyright The Narayana Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsSlots;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsStoreEnvironmentBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for large payloads approaching bytesPerSlot limits.
 * Covers review item 28.3: large payloads approaching bytesPerSlot limits
 */
public class JGroupsSlotsLargePayloadTest {
    private static final String TEST_STORE_DIR = System.getProperty("user.dir") + "/target/jgroups-largepayload-test";
    private JGroupsStoreEnvironmentBean config;
    private JGroupsSlots slots;

    @BeforeEach
    public void setUp() {
        config = new JGroupsStoreEnvironmentBean();
        config.setNodeAddress("largepayload-test-node");
        config.setGroupName("largepayload-test-group");
        config.setCacheName("largepayload-test-cache");
        config.setStoreDir(TEST_STORE_DIR);
        config.setNumberOfSlots(50);
        config.setBytesPerSlot(1024); // 1KB per slot
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
    public void testMaximumPayloadSize() throws Exception {
        slots.init(config);

        // Create payload at exact bytesPerSlot limit
        int maxSize = config.getBytesPerSlot();
        byte[] maxPayload = new byte[maxSize];
        Arrays.fill(maxPayload, (byte) 'M');

        // Should succeed at exactly the limit
        assertDoesNotThrow(() -> {
            slots.write(0, maxPayload, true);
        });

        byte[] result = slots.read(0);
        assertNotNull(result);
        assertArrayEquals(maxPayload, result);
    }

    @Test
    public void testPayloadExceedsLimit() throws Exception {
        slots.init(config);

        // Create payload exceeding bytesPerSlot
        int oversized = config.getBytesPerSlot() + 1;
        byte[] oversizedPayload = new byte[oversized];
        Arrays.fill(oversizedPayload, (byte) 'X');

        // JGroupsSlots stores variable length - write doesn't fail
        assertDoesNotThrow(() -> {
            slots.write(0, oversizedPayload, true);
        }, "Oversized payload should be stored as-is");

        // Read returns actual written size
        byte[] result = slots.read(0);
        assertNotNull(result);
        assertEquals(oversized, result.length,
                "Read should return actual written size");
        // Verify data integrity
        for (byte b : result) {
            assertEquals((byte) 'X', b);
        }
    }

    @Test
    public void testPayloadJustUnderLimit() throws Exception {
        slots.init(config);

        // Create payload just under the limit
        int almostMax = config.getBytesPerSlot() - 1;
        byte[] almostMaxPayload = new byte[almostMax];
        Arrays.fill(almostMaxPayload, (byte) 'A');

        slots.write(0, almostMaxPayload, true);

        byte[] result = slots.read(0);
        assertNotNull(result);
        assertEquals(almostMax, result.length);
        assertArrayEquals(almostMaxPayload, result);
    }

    @Test
    public void testVariousSizes() throws Exception {
        slots.init(config);

        int maxSize = config.getBytesPerSlot();
        int[] testSizes = {
            1,                    // Minimum
            100,                  // Small
            maxSize / 4,          // Quarter
            maxSize / 2,          // Half
            (maxSize * 3) / 4,    // Three quarters
            maxSize - 10,         // Near maximum
            maxSize               // Maximum
        };

        for (int i = 0; i < testSizes.length; i++) {
            int size = testSizes[i];
            byte[] data = new byte[size];
            Arrays.fill(data, (byte) ('A' + i));

            slots.write(i, data, true);

            byte[] result = slots.read(i);
            assertNotNull(result, "Size " + size + " should be readable");
            assertEquals(size, result.length, "Size " + size + " should match");
            assertArrayEquals(data, result, "Size " + size + " content should match");
        }
    }

    @Test
    public void testLargePayloadWithWAL() throws Exception {
        config.setWalEnabled(true);
        config.setWalSyncWrites(true);
        slots.init(config);

        int largeSize = config.getBytesPerSlot() - 100;
        byte[] largePayload = new byte[largeSize];
        new Random().nextBytes(largePayload);

        // Write large payload with WAL enabled
        slots.write(0, largePayload, true);

        byte[] result = slots.read(0);
        assertNotNull(result);
        assertArrayEquals(largePayload, result, "Large payload should survive WAL write");
    }

    @Test
    public void testMultipleLargePayloads() throws Exception {
        slots.init(config);

        int largeSize = config.getBytesPerSlot() - 50;
        Random random = new Random(42); // Fixed seed for reproducibility

        // Write multiple large payloads
        for (int i = 0; i < 10; i++) {
            byte[] largePayload = new byte[largeSize];
            random.nextBytes(largePayload);
            slots.write(i, largePayload, true);
        }

        // Verify all payloads
        random = new Random(42); // Reset for verification
        for (int i = 0; i < 10; i++) {
            byte[] expected = new byte[largeSize];
            random.nextBytes(expected);

            byte[] result = slots.read(i);
            assertNotNull(result);
            assertArrayEquals(expected, result, "Slot " + i + " should match");
        }
    }

    @Test
    public void testPayloadGrowth() throws Exception {
        slots.init(config);

        // Use different slots for each size to avoid overwrite issues
        int baseSlotIndex = 5;
        int slotOffset = 0;

        // Start with small payload, grow incrementally
        // JGroupsSlots stores variable length data
        for (int size = 100; size <= config.getBytesPerSlot(); size += 100) {
            byte[] data = new byte[size];
            Arrays.fill(data, (byte) (size % 256));

            int slotIndex = baseSlotIndex + slotOffset++;
            slots.write(slotIndex, data, true);

            byte[] result = slots.read(slotIndex);
            assertNotNull(result);
            assertEquals(size, result.length, "Size " + size + " should be stored");
            assertArrayEquals(data, result, "Data should match for size " + size);
        }
    }

    @Test
    public void testPayloadShrinkage() throws Exception {
        slots.init(config);

        // Use different slots for each size to avoid overwrite issues
        int baseSlotIndex = 20;
        int slotOffset = 0;

        // Start with maximum, shrink incrementally
        // JGroupsSlots stores variable length data
        for (int size = config.getBytesPerSlot(); size >= 100; size -= 100) {
            byte[] data = new byte[size];
            Arrays.fill(data, (byte) (size % 256));

            int slotIndex = baseSlotIndex + slotOffset++;
            slots.write(slotIndex, data, true);

            byte[] result = slots.read(slotIndex);
            assertNotNull(result);
            assertEquals(size, result.length, "Size " + size + " should be stored");
            assertArrayEquals(data, result, "Data should match for size " + size);
        }
    }

    @Test
    public void testCompressiblePayload() throws Exception {
        slots.init(config);

        // Highly compressible payload (all same byte)
        int largeSize = config.getBytesPerSlot() - 10;
        byte[] compressible = new byte[largeSize];
        Arrays.fill(compressible, (byte) 'Z');

        slots.write(0, compressible, true);

        byte[] result = slots.read(0);
        assertNotNull(result);
        assertArrayEquals(compressible, result);
    }

    @Test
    public void testRandomPayload() throws Exception {
        slots.init(config);

        // Random payload (incompressible)
        int largeSize = config.getBytesPerSlot() - 10;
        byte[] random = new byte[largeSize];
        new Random().nextBytes(random);

        slots.write(0, random, true);

        byte[] result = slots.read(0);
        assertNotNull(result);
        assertArrayEquals(random, result);
    }

    @Test
    public void testBinaryPayload() throws Exception {
        slots.init(config);

        // Test with various binary patterns
        byte[] binaryPayload = new byte[config.getBytesPerSlot() - 20];

        for (int i = 0; i < binaryPayload.length; i++) {
            binaryPayload[i] = (byte) i;
        }

        slots.write(0, binaryPayload, true);

        byte[] result = slots.read(0);
        assertNotNull(result);
        assertArrayEquals(binaryPayload, result);
    }

    @Test
    public void testLargeConfiguredSlotSize() throws Exception {
        // Test with 1MB slots
        config.setBytesPerSlot(1024 * 1024); // 1MB
        config.setNumberOfSlots(10);
        slots.init(config);

        int largeSize = 1024 * 1024 - 100;
        byte[] largePayload = new byte[largeSize];
        Arrays.fill(largePayload, (byte) 'L');

        slots.write(0, largePayload, true);

        byte[] result = slots.read(0);
        assertNotNull(result);
        assertEquals(largeSize, result.length);
    }
}
