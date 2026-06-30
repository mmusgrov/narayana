/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.ByteArrayKey;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.blocks.ReplCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.hp.mwtests.ts.arjuna.objectstore.jgroups.JGroupsTestBase.REPLICATION_TIMEOUT_MS;
import static com.hp.mwtests.ts.arjuna.objectstore.jgroups.JGroupsTestBase.waitFor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests JGroups ReplCache clustering at the cache level, demonstrating that
 * multiple cache instances can form a cluster and replicate data.
 *
 * This avoids RecoveryStore singleton issues by testing the underlying
 * cache replication mechanism directly.
 */
public class JGroupsCacheLevelClusterTest {

    private static final String CLUSTER_NAME = "cache-test-" + System.currentTimeMillis();
    private final List<ReplCache<ByteArrayKey, byte[]>> caches = new ArrayList<>();

    /**
     * View change listener that counts down when expected cluster size is reached
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
            System.out.println("View change: cluster now has " + currentSize + " members (expecting " + expectedSize + ")");
            if (currentSize >= expectedSize) {
                latch.countDown();
            }
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        int getCurrentSize() {
            return currentSize;
        }
    }

    @AfterEach
    public void tearDown() {
        // Stop all caches
        for (ReplCache<ByteArrayKey, byte[]> cache : caches) {
            try {
                cache.stop();
            } catch (Exception e) {
                System.err.println("Error stopping cache: " + e.getMessage());
            }
        }
        caches.clear();
    }

    /**
     * Creates a cache instance and adds it to the cleanup list
     */
    private ReplCache<ByteArrayKey, byte[]> createCache(String nodeName) throws Exception {
        ReplCache<ByteArrayKey, byte[]> cache = new ReplCache<>("jgroups.xml", CLUSTER_NAME);
        cache.setCallTimeout(1500L);
        cache.setCachingTime(30000L); // 30 second TTL
        cache.setMigrateData(true);
        caches.add(cache);
        return cache;
    }

    /**
     * Test that 3 caches can form a cluster
     */
    @Test
    public void testClusterFormation() throws Exception {
        // Create cache1
        ReplCache<ByteArrayKey, byte[]> cache1 = createCache("node1");
        ClusterFormationListener listener1 = new ClusterFormationListener(1);
        cache1.addReceiver(listener1);
        cache1.start();
        assertTrue(listener1.await(10, TimeUnit.SECONDS), "Cache1 should start");
        assertEquals(1, cache1.getClusterSize(), "Cache1 should see 1 member");

        // Create cache2 - both caches should see size 2
        ReplCache<ByteArrayKey, byte[]> cache2 = createCache("node2");
        ClusterFormationListener listener2 = new ClusterFormationListener(2);
        cache1.addReceiver(listener2); // Listen on cache1 for size 2
        cache2.start();
        assertTrue(listener2.await(10, TimeUnit.SECONDS), "Cluster should reach size 2");

        waitFor(REPLICATION_TIMEOUT_MS, "view propagation to 2 members",
            () -> cache1.getClusterSize() == 2 && cache2.getClusterSize() == 2);

        // Create cache3 - all should see size 3
        ReplCache<ByteArrayKey, byte[]> cache3 = createCache("node3");
        ClusterFormationListener listener3 = new ClusterFormationListener(3);
        cache1.addReceiver(listener3);
        cache3.start();
        assertTrue(listener3.await(10, TimeUnit.SECONDS), "Cluster should reach size 3");

        waitFor(REPLICATION_TIMEOUT_MS, "view propagation to 3 members",
            () -> cache1.getClusterSize() == 3 && cache2.getClusterSize() == 3 && cache3.getClusterSize() == 3);

        System.out.println("✓ Cluster formation verified: 3 nodes");
    }

    /**
     * Test that data written to one cache is visible on others
     */
    @Test
    public void testDataReplication() throws Exception {
        // Form a 3-node cluster
        ReplCache<ByteArrayKey, byte[]> cache1 = createCache("node1");
        ReplCache<ByteArrayKey, byte[]> cache2 = createCache("node2");
        ReplCache<ByteArrayKey, byte[]> cache3 = createCache("node3");

        ClusterFormationListener listener = new ClusterFormationListener(3);
        cache1.addReceiver(listener);

        cache1.start();
        cache2.start();
        cache3.start();

        assertTrue(listener.await(10, TimeUnit.SECONDS), "Cluster should form");
        waitFor(REPLICATION_TIMEOUT_MS, "cluster view stabilization",
            () -> cache1.getClusterSize() == 3 && cache2.getClusterSize() == 3 && cache3.getClusterSize() == 3);

        // Write data on cache1
        ByteArrayKey key = new ByteArrayKey(new Uid().getBytes());
        byte[] value = "transaction-log-data".getBytes();

        cache1.put(key, value, (short) -1, 0); // -1 = replicate to all

        waitFor(REPLICATION_TIMEOUT_MS, "data replication to cache2",
            () -> Arrays.equals(value, cache2.get(key)));
        waitFor(REPLICATION_TIMEOUT_MS, "data replication to cache3",
            () -> Arrays.equals(value, cache3.get(key)));

        System.out.println("✓ Data replication verified across 3 nodes");
    }

    /**
     * Test that data removed from one cache is removed from others
     */
    @Test
    public void testDataRemoval() throws Exception {
        // Form a 2-node cluster
        ReplCache<ByteArrayKey, byte[]> cache1 = createCache("node1");
        ReplCache<ByteArrayKey, byte[]> cache2 = createCache("node2");

        ClusterFormationListener listener = new ClusterFormationListener(2);
        cache1.addReceiver(listener);

        cache1.start();
        cache2.start();

        assertTrue(listener.await(10, TimeUnit.SECONDS), "Cluster should form");
        waitFor(REPLICATION_TIMEOUT_MS, "view stabilization to 2 members",
            () -> cache1.getClusterSize() == 2 && cache2.getClusterSize() == 2);

        // Write and verify replication
        ByteArrayKey key = new ByteArrayKey(new Uid().getBytes());
        byte[] value = "data-to-be-removed".getBytes();

        cache1.put(key, value, (short) -1, 0);
        waitFor(REPLICATION_TIMEOUT_MS, "data replication to cache2",
            () -> cache2.get(key) != null);

        // Remove from cache1
        cache1.remove(key);
        waitFor(REPLICATION_TIMEOUT_MS, "removal replication",
            () -> cache1.get(key) == null && cache2.get(key) == null);

        System.out.println("✓ Data removal replication verified");
    }

    /**
     * Test multiple concurrent writes from different nodes
     */
    @Test
    public void testConcurrentWrites() throws Exception {
        // Form a 3-node cluster
        ReplCache<ByteArrayKey, byte[]> cache1 = createCache("node1");
        ReplCache<ByteArrayKey, byte[]> cache2 = createCache("node2");
        ReplCache<ByteArrayKey, byte[]> cache3 = createCache("node3");

        ClusterFormationListener listener = new ClusterFormationListener(3);
        cache1.addReceiver(listener);

        cache1.start();
        cache2.start();
        cache3.start();

        assertTrue(listener.await(10, TimeUnit.SECONDS), "Cluster should form");
        waitFor(REPLICATION_TIMEOUT_MS, "view stabilization to 3 members",
            () -> cache1.getClusterSize() == 3 && cache2.getClusterSize() == 3 && cache3.getClusterSize() == 3);

        // Write different keys from each cache
        ByteArrayKey key1 = new ByteArrayKey(new Uid().getBytes());
        ByteArrayKey key2 = new ByteArrayKey(new Uid().getBytes());
        ByteArrayKey key3 = new ByteArrayKey(new Uid().getBytes());

        cache1.put(key1, "from-node1".getBytes(), (short) -1, 0);
        cache2.put(key2, "from-node2".getBytes(), (short) -1, 0);
        cache3.put(key3, "from-node3".getBytes(), (short) -1, 0);

        waitFor(REPLICATION_TIMEOUT_MS, "replication of all keys to all caches",
            () -> cache1.get(key1) != null && cache1.get(key2) != null && cache1.get(key3) != null
               && cache2.get(key1) != null && cache2.get(key2) != null && cache2.get(key3) != null
               && cache3.get(key1) != null && cache3.get(key2) != null && cache3.get(key3) != null);

        System.out.println("✓ Concurrent writes from multiple nodes verified");
    }

    /**
     * Test that a node leaving doesn't affect data on remaining nodes
     */
    @Test
    public void testNodeLeaving() throws Exception {
        // Form a 3-node cluster
        ReplCache<ByteArrayKey, byte[]> cache1 = createCache("node1");
        ReplCache<ByteArrayKey, byte[]> cache2 = createCache("node2");
        ReplCache<ByteArrayKey, byte[]> cache3 = createCache("node3");

        ClusterFormationListener formListener = new ClusterFormationListener(3);
        cache1.addReceiver(formListener);

        cache1.start();
        cache2.start();
        cache3.start();

        assertTrue(formListener.await(10, TimeUnit.SECONDS), "Cluster should form");
        waitFor(REPLICATION_TIMEOUT_MS, "view stabilization to 3 members",
            () -> cache1.getClusterSize() == 3 && cache2.getClusterSize() == 3 && cache3.getClusterSize() == 3);

        // Write data replicated to all nodes
        ByteArrayKey key = new ByteArrayKey(new Uid().getBytes());
        byte[] value = "persistent-data".getBytes();
        cache1.put(key, value, (short) -1, 0);
        waitFor(REPLICATION_TIMEOUT_MS, "data replication to all caches",
            () -> Arrays.equals(value, cache1.get(key)) && Arrays.equals(value, cache2.get(key)));

        // Stop cache3
        cache3.stop();
        caches.remove(cache3);

        waitFor(REPLICATION_TIMEOUT_MS, "cluster shrink to 2 members",
            () -> cache1.getClusterSize() == 2 && cache2.getClusterSize() == 2);

        // Data should still be on cache1 and cache2
        assertArrayEquals(value, cache1.get(key), "Cache1 data should be intact");
        assertArrayEquals(value, cache2.get(key), "Cache2 data should be intact");

        System.out.println("✓ Node leaving doesn't affect remaining data");
    }
}
