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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for concurrent operations on JGroupsSlots.
 * Covers review item 28.2: concurrent writes to the same slot
 */
public class JGroupsSlotsConcurrencyTest {
    private static final String TEST_STORE_DIR = System.getProperty("user.dir") + "/target/jgroups-concurrency-test";
    private JGroupsStoreEnvironmentBean config;
    private JGroupsSlots slots;

    @BeforeEach
    public void setUp() {
        config = new JGroupsStoreEnvironmentBean();
        config.setNodeAddress("concurrency-test-node");
        config.setGroupName("concurrency-test-group");
        config.setCacheName("concurrency-test-cache");
        config.setStoreDir(TEST_STORE_DIR);
        config.setNumberOfSlots(100);
        config.setBytesPerSlot(1024);
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
    public void testConcurrentWritesToSameSlot() throws Exception {
        slots.init(config);

        int numThreads = 10;
        int slotIndex = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Ensure all threads start together
                    byte[] data = ("thread-" + threadId).getBytes();
                    slots.write(slotIndex, data, true);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errors.add("Thread " + threadId + ": " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();

        assertTrue(errors.isEmpty(), "No exceptions expected, but got: " + String.join(", ", errors));
        assertEquals(numThreads, successCount.get(), "All writes should succeed");

        // The final value should be from one of the threads
        byte[] result = slots.read(slotIndex);
        assertNotNull(result);
        String resultStr = new String(result);
        assertTrue(resultStr.startsWith("thread-"), "Result should be from one of the threads: " + resultStr);
    }

    @Test
    public void testConcurrentWritesToDifferentSlots() throws Exception {
        slots.init(config);

        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int slotIndex = i;
            executor.submit(() -> {
                try {
                    byte[] data = ("slot-" + slotIndex).getBytes();
                    slots.write(slotIndex, data, true);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(numThreads, successCount.get(), "All writes should succeed");

        // Verify all slots have correct data
        for (int i = 0; i < numThreads; i++) {
            byte[] result = slots.read(i);
            assertNotNull(result);
            assertEquals("slot-" + i, new String(result));
        }
    }

    @Test
    public void testConcurrentReadAndWrite() throws Exception {
        slots.init(config);

        int slotIndex = 0;
        byte[] initialData = "initial".getBytes();
        slots.write(slotIndex, initialData, true);

        int numReaders = 10;
        int numWriters = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numReaders + numWriters);
        CountDownLatch doneLatch = new CountDownLatch(numReaders + numWriters);
        AtomicInteger readsCompleted = new AtomicInteger(0);
        AtomicInteger writesCompleted = new AtomicInteger(0);

        // Start readers
        for (int i = 0; i < numReaders; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        byte[] data = slots.read(slotIndex);
                        assertNotNull(data);
                    }
                    readsCompleted.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start writers
        for (int i = 0; i < numWriters; i++) {
            final int writerId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        byte[] data = ("writer-" + writerId + "-" + j).getBytes();
                        slots.write(slotIndex, data, true);
                    }
                    writesCompleted.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(numReaders, readsCompleted.get());
        assertEquals(numWriters, writesCompleted.get());
    }

    @Test
    public void testConcurrentClearAndWrite() throws Exception {
        slots.init(config);

        int slotIndex = 10;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch doneLatch = new CountDownLatch(20);

        // 10 threads writing
        for (int i = 0; i < 10; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 50; j++) {
                        byte[] data = ("write-" + threadId + "-" + j).getBytes();
                        slots.write(slotIndex, data, true);
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 10 threads clearing
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 50; j++) {
                        slots.clear(slotIndex, true);
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Final state should be valid (either clear or a write value)
        byte[] result = slots.read(slotIndex);
        // Either null/empty (cleared) or contains data (from a write)
        assertTrue(result == null || result.length == 0 || new String(result).startsWith("write-"));
    }

    @Test
    public void testConcurrentWALWrites() throws Exception {
        config.setWalEnabled(true);
        config.setWalSyncWrites(false); // Async for performance
        slots.init(config);

        int numThreads = 15;
        int writesPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger totalWrites = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < writesPerThread; j++) {
                        int slotIndex = (threadId * writesPerThread + j) % config.getNumberOfSlots();
                        byte[] data = ("wal-" + threadId + "-" + j).getBytes();
                        slots.write(slotIndex, data, true);
                        totalWrites.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(numThreads * writesPerThread, totalWrites.get());
    }

    @Test
    public void testStressTest() throws Exception {
        slots.init(config);

        int numThreads = 50;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int j = 0; j < operationsPerThread; j++) {
                        int slotIndex = random.nextInt(config.getNumberOfSlots());
                        int operation = random.nextInt(3);

                        switch (operation) {
                            case 0: // write
                                byte[] data = ("thread-" + threadId + "-op-" + j).getBytes();
                                slots.write(slotIndex, data, true);
                                break;
                            case 1: // read
                                slots.read(slotIndex);
                                break;
                            case 2: // clear
                                slots.clear(slotIndex, true);
                                break;
                        }
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errors.add("Thread " + threadId + ": " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(doneLatch.await(60, TimeUnit.SECONDS), "Stress test should complete");
        executor.shutdown();

        assertTrue(errors.isEmpty(), "No exceptions expected, but got: " + String.join("; ", errors));
        assertEquals(numThreads, successCount.get(), "All threads should complete successfully");
    }
}
