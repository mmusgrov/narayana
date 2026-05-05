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
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

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

    @After
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
        assertTrue("Cache1 should start", listener1.await(10, TimeUnit.SECONDS));
        assertEquals("Cache1 should see 1 member", 1, cache1.getClusterSize());

        // Create cache2 - both caches should see size 2
        ReplCache<ByteArrayKey, byte[]> cache2 = createCache("node2");
        ClusterFormationListener listener2 = new ClusterFormationListener(2);
        cache1.addReceiver(listener2); // Listen on cache1 for size 2
        cache2.start();
        assertTrue("Cluster should reach size 2", listener2.await(10, TimeUnit.SECONDS));

        // Give view time to propagate
        Thread.sleep(500);
        assertEquals("Cache1 should see 2 members", 2, cache1.getClusterSize());
        assertEquals("Cache2 should see 2 members", 2, cache2.getClusterSize());

        // Create cache3 - all should see size 3
        ReplCache<ByteArrayKey, byte[]> cache3 = createCache("node3");
        ClusterFormationListener listener3 = new ClusterFormationListener(3);
        cache1.addReceiver(listener3);
        cache3.start();
        assertTrue("Cluster should reach size 3", listener3.await(10, TimeUnit.SECONDS));

        Thread.sleep(500);
        assertEquals("Cache1 should see 3 members", 3, cache1.getClusterSize());
        assertEquals("Cache2 should see 3 members", 3, cache2.getClusterSize());
        assertEquals("Cache3 should see 3 members", 3, cache3.getClusterSize());

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

        assertTrue("Cluster should form", listener.await(10, TimeUnit.SECONDS));
        Thread.sleep(500); // Allow view to stabilize

        // Write data on cache1
        ByteArrayKey key = new ByteArrayKey(new Uid().getBytes());
        byte[] value = "transaction-log-data".getBytes();

        cache1.put(key, value, (short) -1, 0); // -1 = replicate to all

        // Allow replication to complete
        Thread.sleep(1000);

        // Verify on cache2
        byte[] result2 = cache2.get(key);
        assertNotNull("Cache2 should have the data", result2);
        assertArrayEquals("Cache2 data should match", value, result2);

        // Verify on cache3
        byte[] result3 = cache3.get(key);
        assertNotNull("Cache3 should have the data", result3);
        assertArrayEquals("Cache3 data should match", value, result3);

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

        assertTrue("Cluster should form", listener.await(10, TimeUnit.SECONDS));
        Thread.sleep(500);

        // Write and verify replication
        ByteArrayKey key = new ByteArrayKey(new Uid().getBytes());
        byte[] value = "data-to-be-removed".getBytes();

        cache1.put(key, value, (short) -1, 0);
        Thread.sleep(1000);

        assertNotNull("Cache2 should have the data", cache2.get(key));

        // Remove from cache1
        cache1.remove(key);
        Thread.sleep(1000);

        // Verify removed from both
        assertNull("Cache1 should not have the data", cache1.get(key));
        assertNull("Cache2 should not have the data", cache2.get(key));

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

        assertTrue("Cluster should form", listener.await(10, TimeUnit.SECONDS));
        Thread.sleep(500);

        // Write different keys from each cache
        ByteArrayKey key1 = new ByteArrayKey(new Uid().getBytes());
        ByteArrayKey key2 = new ByteArrayKey(new Uid().getBytes());
        ByteArrayKey key3 = new ByteArrayKey(new Uid().getBytes());

        cache1.put(key1, "from-node1".getBytes(), (short) -1, 0);
        cache2.put(key2, "from-node2".getBytes(), (short) -1, 0);
        cache3.put(key3, "from-node3".getBytes(), (short) -1, 0);

        Thread.sleep(1000);

        // Each cache should see all 3 keys
        assertNotNull("Cache1 should see key1", cache1.get(key1));
        assertNotNull("Cache1 should see key2", cache1.get(key2));
        assertNotNull("Cache1 should see key3", cache1.get(key3));

        assertNotNull("Cache2 should see key1", cache2.get(key1));
        assertNotNull("Cache2 should see key2", cache2.get(key2));
        assertNotNull("Cache2 should see key3", cache2.get(key3));

        assertNotNull("Cache3 should see key1", cache3.get(key1));
        assertNotNull("Cache3 should see key2", cache3.get(key2));
        assertNotNull("Cache3 should see key3", cache3.get(key3));

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

        assertTrue("Cluster should form", formListener.await(10, TimeUnit.SECONDS));
        Thread.sleep(500);

        // Write data replicated to all nodes
        ByteArrayKey key = new ByteArrayKey(new Uid().getBytes());
        byte[] value = "persistent-data".getBytes();
        cache1.put(key, value, (short) -1, 0);
        Thread.sleep(1000);

        // Stop cache3
        ClusterFormationListener leaveListener = new ClusterFormationListener(2);
        cache1.addReceiver(leaveListener);
        cache3.stop();
        caches.remove(cache3);

        assertTrue("Cluster should shrink to 2", leaveListener.await(10, TimeUnit.SECONDS));
        Thread.sleep(500);

        // Data should still be on cache1 and cache2
        byte[] result1 = cache1.get(key);
        byte[] result2 = cache2.get(key);

        assertNotNull("Cache1 should still have data", result1);
        assertNotNull("Cache2 should still have data", result2);
        assertArrayEquals("Cache1 data should be intact", value, result1);
        assertArrayEquals("Cache2 data should be intact", value, result2);

        System.out.println("✓ Node leaving doesn't affect remaining data");
    }
}
