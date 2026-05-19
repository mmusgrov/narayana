/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStoreKey;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.ByteArrayKey;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;

import static org.junit.Assert.*;

/**
 * Tests JGroupsSlots replication across a 3-node cluster.
 * Each node has its own JChannel, JGroupsSlots instance.
 * Writes to one node should be visible on all nodes via JGroups ReplCache replication.
 *
 * This test works at the JGroupsSlots level, bypassing RecoveryStore to avoid
 * singleton issues with BeanPopulator and StoreManager.
 */
public class JGroupsClusterTest extends JGroupsTestBase {

    private static final String TEST_CLUSTER_NAME = "test-cluster-" + System.currentTimeMillis();
    private Store store1, store2, store3;

    @Before
    public void setUp() throws Throwable {
        // Create 3 nodes in the same cluster with unique cluster name
        store1 = createStore("node1", TEST_CLUSTER_NAME, STORE_DIR + "/test1");
        store2 = createStore("node2", TEST_CLUSTER_NAME, STORE_DIR + "/test2");
        store3 = createStore("node3", TEST_CLUSTER_NAME, STORE_DIR + "/test3");

        // Start all stores (connects channels and initializes caches)
        store1.start();
        store2.start();
        store3.start();

        // Give cluster time to form and view to stabilize
        Thread.sleep(3000);

        // Debug: print cache info
        System.out.println("Store1 cache started: " + (store1.config().getCache() != null));
        System.out.println("Store2 cache started: " + (store2.config().getCache() != null));
        System.out.println("Store3 cache started: " + (store3.config().getCache() != null));
    }

    @After
    public void tearDown() throws IOException {
        // Stop all caches (ReplCache manages its own channel lifecycle)
        try {
            if (store1 != null && store1.config().getCache() != null) {
                store1.config().getCache().stop();
            }
        } catch (Exception e) {
            System.err.println("Error stopping store1 cache: " + e.getMessage());
        }

        try {
            if (store2 != null && store2.config().getCache() != null) {
                store2.config().getCache().stop();
            }
        } catch (Exception e) {
            System.err.println("Error stopping store2 cache: " + e.getMessage());
        }

        try {
            if (store3 != null && store3.config().getCache() != null) {
                store3.config().getCache().stop();
            }
        } catch (Exception e) {
            System.err.println("Error stopping store3 cache: " + e.getMessage());
        }

        // Give time for cleanup
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Clean up store directories
        cleanupStoreDir(store1);
        cleanupStoreDir(store2);
        cleanupStoreDir(store3);
    }

    private void cleanupStoreDir(Store store) throws IOException {
        if (store != null && store.path() != null && Files.exists(store.path())) {
            Files.walk(store.path())
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }
    }

    @Test
    public void testClusterFormation() throws Exception {
        // Verify all 3 caches are started and can communicate
        // We verify this by doing a simple put/get operation
        byte[] key = "formation-test-key".getBytes();
        byte[] value = "formation-test-value".getBytes();
        ByteArrayKey cacheKey = new ByteArrayKey(key);

        // Put on node1
        store1.config().getCache().put(cacheKey, value, (short) -1, 5000);

        // Should be able to get from same node immediately
        assertNotNull("Node1 should read its own write", store1.config().getCache().get(cacheKey));

        // Give some time for potential replication
        Thread.sleep(1000);

        // If cluster formed, node2 and node3 should see the value
        assertNotNull("Node2 should see replicated value", store2.config().getCache().get(cacheKey));
        assertNotNull("Node3 should see replicated value", store3.config().getCache().get(cacheKey));
    }

    @Test
    public void testCacheReplicationDirect() throws Exception {
        // Test cache replication directly using ReplCache
        byte[] key1 = "test-key-1".getBytes();
        byte[] value1 = "test-value-1".getBytes();
        ByteArrayKey cacheKey = new ByteArrayKey(key1);

        // Debug: check cache names
        System.out.println("Cache1 name: " + store1.config().getCacheName());
        System.out.println("Cache2 name: " + store2.config().getCacheName());
        System.out.println("Cache3 name: " + store3.config().getCacheName());

        // Put value in node1's cache
        // Use replicationCount=-1 to skip L1 cache and write to L2 (distributed cache)
        System.out.println("Putting value in node1 cache...");
        store1.config().getCache().put(cacheKey, value1, (short) -1, 10000);

        // Verify node1 can read its own write
        byte[] node1Value = store1.config().getCache().get(cacheKey);
        System.out.println("Node1 read back: " + (node1Value != null ? "SUCCESS" : "FAILED"));

        // Give replication time
        Thread.sleep(2000);

        // Read from node2's cache - should see replicated value
        System.out.println("Reading from node2 cache...");
        byte[] node2Value = store2.config().getCache().get(cacheKey);
        System.out.println("Node2 read: " + (node2Value != null ? "SUCCESS" : "FAILED (null)"));
        assertNotNull("Node2 should see replicated value", node2Value);
        assertArrayEquals("Node2 value should match", value1, node2Value);

        // Read from node3's cache - should also see replicated value
        byte[] node3Value = store3.config().getCache().get(cacheKey);
        assertNotNull("Node3 should see replicated value", node3Value);
        assertArrayEquals("Node3 value should match", value1, node3Value);
    }

    @Test
    public void testSlotWriteReplication() throws Exception {
        // Test slot-level replication by writing to a specific slot
        int slotIndex = 0; // Use first slot
        byte[] testData = "test-slot-data-replication".getBytes();

        // Write data to slot 0 on node1
        store1.slots().write(slotIndex, testData, true);

        // Give replication time
        Thread.sleep(2000);

        // Read from node2's slots - should see replicated data
        byte[] node2Data = store2.slots().read(slotIndex);
        assertNotNull("Node2 should read replicated slot data", node2Data);
        assertArrayEquals("Node2 slot data should match", testData, node2Data);

        // Read from node3's slots - should also see replicated data
        byte[] node3Data = store3.slots().read(slotIndex);
        assertNotNull("Node3 should read replicated slot data", node3Data);
        assertArrayEquals("Node3 slot data should match", testData, node3Data);

        // Update the data from node2
        byte[] updatedData = "updated-slot-data".getBytes();
        store2.slots().write(slotIndex, updatedData, true);

        // Give replication time
        Thread.sleep(2000);

        // All nodes should see the updated data
        byte[] node1Updated = store1.slots().read(slotIndex);
        assertArrayEquals("Node1 should see updated data", updatedData, node1Updated);

        byte[] node3Updated = store3.slots().read(slotIndex);
        assertArrayEquals("Node3 should see updated data", updatedData, node3Updated);
    }

    @Test
    public void testMultiNodeCacheWrites() throws Exception {
        // Test writing from different nodes
        byte[] key1 = "key-from-node1".getBytes();
        byte[] key2 = "key-from-node2".getBytes();
        byte[] key3 = "key-from-node3".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        byte[] value3 = "value3".getBytes();

        ByteArrayKey cacheKey1 = new ByteArrayKey(key1);
        ByteArrayKey cacheKey2 = new ByteArrayKey(key2);
        ByteArrayKey cacheKey3 = new ByteArrayKey(key3);

        // Write from each node
        store1.config().getCache().put(cacheKey1, value1, (short) -1, 10000);
        store2.config().getCache().put(cacheKey2, value2, (short) -1, 10000);
        store3.config().getCache().put(cacheKey3, value3, (short) -1, 10000);

        // Give replication time
        Thread.sleep(2000);

        // All nodes should see all three values
        assertNotNull("Node1 should see key1", store1.config().getCache().get(cacheKey1));
        assertNotNull("Node1 should see key2", store1.config().getCache().get(cacheKey2));
        assertNotNull("Node1 should see key3", store1.config().getCache().get(cacheKey3));

        assertNotNull("Node2 should see key1", store2.config().getCache().get(cacheKey1));
        assertNotNull("Node2 should see key2", store2.config().getCache().get(cacheKey2));
        assertNotNull("Node2 should see key3", store2.config().getCache().get(cacheKey3));

        assertNotNull("Node3 should see key1", store3.config().getCache().get(cacheKey1));
        assertNotNull("Node3 should see key2", store3.config().getCache().get(cacheKey2));
        assertNotNull("Node3 should see key3", store3.config().getCache().get(cacheKey3));
    }

    @Test
    public void testCacheDeleteReplication() throws Exception {
        byte[] key = "key-to-delete".getBytes();
        byte[] value = "value-to-delete".getBytes();
        ByteArrayKey cacheKey = new ByteArrayKey(key);

        // Write on node1
        store1.config().getCache().put(cacheKey, value, (short) -1, 10000);

        // Wait for replication
        Thread.sleep(2000);

        // Verify all nodes have it
        assertNotNull(store1.config().getCache().get(cacheKey));
        assertNotNull(store2.config().getCache().get(cacheKey));
        assertNotNull(store3.config().getCache().get(cacheKey));

        // Delete from node2
        store2.config().getCache().remove(cacheKey);

        // Wait for delete to replicate
        Thread.sleep(2000);

        // All nodes should no longer have it
        assertNull(store1.config().getCache().get(cacheKey));
        assertNull(store2.config().getCache().get(cacheKey));
        assertNull(store3.config().getCache().get(cacheKey));
    }
}
