/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.ByteArrayKey;
import org.jgroups.blocks.ReplCache;
import org.junit.Before;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JGroupsReplicatedTest extends JGroupsTestBase {

    @Before
    public void setup() {
    }

    /*
     * Test JGroups replicated caches. Unlike Infinispan, JGroups ReplCache is primarily an in-memory cache
     * without built-in write-through persistence. However, we can still verify:
     * - Data replication across nodes
     * - Slot store consistency across replicas
     * - Basic cache operations
     *
     * Note: JGroups ReplCache does not provide the same built-in persistence as Infinispan's cache stores.
     * For persistence in a JGroups-based system, additional mechanisms would need to be implemented.
     */
    @Test
    public void testReplicatedCache() throws Exception {
        String storeDir1 = "jgroups-caches/replicated1";
        String storeDir2 = "jgroups-caches/replicated2";

        Store store1 = createStore("node1", null, storeDir1);
        Store store2 = createStore("node2", null, storeDir2);

        store1.config().setSlotKeyGeneratorClassName(JGroupsClusterMemberId.class.getName());
        store2.config().setSlotKeyGeneratorClassName(JGroupsClusterMemberId.class.getName());

        Assertions.assertTrue(store1.config().getStoreDir().endsWith("replicated1/node1"));
        Assertions.assertTrue(store2.config().getStoreDir().endsWith("replicated2/node2"));

        // Start the first store
        store1.start();
        ReplCache<ByteArrayKey, byte[]> cache1 = store1.config().getCache();
        Thread.sleep(1000); // Give time for the cluster to form

        // create two key value pairs
        record KVPair(ByteArrayKey key, byte[] value) {}
        KVPair kv1 = new KVPair(new ByteArrayKey("key1".getBytes()), "value1".getBytes());
        KVPair kv2 = new KVPair(new ByteArrayKey("key2".getBytes()), "value2".getBytes());

        // populate the first JGroups cache1 with them
        cache1.put(kv1.key, kv1.value);
        cache1.put(kv2.key, kv2.value);

        // Start the second store - it should join the cluster and receive replicated data
        store2.start();
        ReplCache<ByteArrayKey, byte[]> cache2 = store2.config().getCache();
        Thread.sleep(2000); // Give time for replication to complete

        // Verify it replicates to cache2
        byte[] c1kv1Value = cache1.get(kv1.key);
        byte[] c2kv1Value = cache2.get(kv1.key);
        Assertions.assertArrayEquals(kv1.value, cache1.get(kv1.key));
        Assertions.assertArrayEquals(kv1.value, cache2.get(kv1.key));
        Assertions.assertArrayEquals(kv2.value, cache1.get(kv2.key));
        Assertions.assertArrayEquals(kv2.value, cache2.get(kv2.key));

        // Verify that the slot stores at each node have the same values
        // note that the slots backend is internal, but it's still useful to test it directly
        byte[] value1 = store1.slots().read(0);
        byte[] value2 = store2.slots().read(0);
        byte[] value3 = store1.slots().read(1);
        byte[] value4 = store2.slots().read(1);

        Assertions.assertArrayEquals(value1, value2);
        Assertions.assertArrayEquals(value3, value4);

        // Clean up
        store1.slots().clear(0, true);
        store1.slots().clear(1, true);

        store1.stop();
        store2.stop();
    }

    /*
     * Test that data is properly replicated when nodes join at different times
     */
    @Test
    public void testLateJoiningNode() throws Exception {
        String storeDir1 = "jgroups-caches/late-join1";
        String storeDir2 = "jgroups-caches/late-join2";

        Store store1 = createStore("node1", null, storeDir1);
        Store store2 = createStore("node2", null, storeDir2);

        store1.config().setSlotKeyGeneratorClassName(JGroupsClusterMemberId.class.getName());
        store2.config().setSlotKeyGeneratorClassName(JGroupsClusterMemberId.class.getName());

        // Start only the first store
        store1.start();
        Thread.sleep(1000);

        // Add data before second node joins
        record KVPair(ByteArrayKey key, byte[] value) {}
        KVPair kv1 = new KVPair(new ByteArrayKey("early-key1".getBytes()), "early-value1".getBytes());
        KVPair kv2 = new KVPair(new ByteArrayKey("early-key2".getBytes()), "early-value2".getBytes());

        store1.config().getCache().put(kv1.key, kv1.value);
        store1.config().getCache().put(kv2.key, kv2.value);

        // Verify data is in store1
        Assertions.assertArrayEquals(kv1.value, store1.config().getCache().get(kv1.key));
        Assertions.assertArrayEquals(kv2.value, store1.config().getCache().get(kv2.key));

        // Now start the second store - it should join and receive the existing data
        store2.start();
        Thread.sleep(3000); // Give extra time for state transfer

        // Verify the late-joining node received the replicated data
        Assertions.assertArrayEquals(kv1.value, store2.config().getCache().get(kv1.key));
        Assertions.assertArrayEquals(kv2.value, store2.config().getCache().get(kv2.key));

        // Add more data after both nodes are up
        KVPair kv3 = new KVPair(new ByteArrayKey("late-key3".getBytes()), "late-value3".getBytes());
        store2.config().getCache().put(kv3.key, kv3.value);
        Thread.sleep(1000);

        // Verify it's replicated to store1
        Assertions.assertArrayEquals(kv3.value, store1.config().getCache().get(kv3.key));

        // Clean up
        store1.slots().clear(0, true);
        store1.slots().clear(1, true);
        store1.slots().clear(2, true);

        store1.stop();
        store2.stop();
    }

    /*
     * Test node failure and remaining node operation
     */
    @Test
    public void testNodeFailure() throws Exception {
        String storeDir1 = "jgroups-caches/failure1";
        String storeDir2 = "jgroups-caches/failure2";
        String storeDir3 = "jgroups-caches/failure3";

        Store store1 = createStore("node1", null, storeDir1);
        Store store2 = createStore("node2", null, storeDir2);
        Store store3 = createStore("node3", null, storeDir3);

        store1.config().setSlotKeyGeneratorClassName(JGroupsClusterMemberId.class.getName());
        store2.config().setSlotKeyGeneratorClassName(JGroupsClusterMemberId.class.getName());
        store3.config().setSlotKeyGeneratorClassName(JGroupsClusterMemberId.class.getName());

        // Start all three stores
        store1.start();
        Thread.sleep(1000);
        store2.start();
        Thread.sleep(1000);
        store3.start();
        Thread.sleep(2000); // Give time for cluster to form

        // Add data
        record KVPair(ByteArrayKey key, byte[] value) {}
        KVPair kv1 = new KVPair(new ByteArrayKey("key1".getBytes()), "value1".getBytes());
        KVPair kv2 = new KVPair(new ByteArrayKey("key2".getBytes()), "value2".getBytes());

        store1.config().getCache().put(kv1.key, kv1.value);
        store2.config().getCache().put(kv2.key, kv2.value);
        Thread.sleep(1000);

        // Verify all nodes have all data
        Assertions.assertArrayEquals(kv1.value, store1.config().getCache().get(kv1.key));
        Assertions.assertArrayEquals(kv1.value, store2.config().getCache().get(kv1.key));
        Assertions.assertArrayEquals(kv1.value, store3.config().getCache().get(kv1.key));
        Assertions.assertArrayEquals(kv2.value, store1.config().getCache().get(kv2.key));
        Assertions.assertArrayEquals(kv2.value, store2.config().getCache().get(kv2.key));
        Assertions.assertArrayEquals(kv2.value, store3.config().getCache().get(kv2.key));

        // Simulate node2 failure
        store2.stop();
        Thread.sleep(2000);

        // Verify data is still accessible from remaining nodes
        Assertions.assertArrayEquals(kv1.value, store1.config().getCache().get(kv1.key));
        Assertions.assertArrayEquals(kv1.value, store3.config().getCache().get(kv1.key));
        Assertions.assertArrayEquals(kv2.value, store1.config().getCache().get(kv2.key));
        Assertions.assertArrayEquals(kv2.value, store3.config().getCache().get(kv2.key));

        // Add new data after node2 is down
        KVPair kv3 = new KVPair(new ByteArrayKey("key3".getBytes()), "value3".getBytes());
        store3.config().getCache().put(kv3.key, kv3.value);
        Thread.sleep(1000);

        // Verify it's replicated to store1 but not the failed store2
        Assertions.assertArrayEquals(kv3.value, store1.config().getCache().get(kv3.key));
        Assertions.assertArrayEquals(kv3.value, store3.config().getCache().get(kv3.key));

        // Clean up
        store1.slots().clear(0, true);
        store1.slots().clear(1, true);
        store1.slots().clear(2, true);

        store1.stop();
        store3.stop();
    }

    /*
     * Test slot consistency across multiple nodes
     */
    @Test
    public void testSlotConsistency() throws Exception {
        String storeDir1 = "jgroups-caches/consistency1";
        String storeDir2 = "jgroups-caches/consistency2";

        Store store1 = createStore("node1", null, storeDir1);
        Store store2 = createStore("node2", null, storeDir2);

        store1.config().setSlotKeyGeneratorClassName(JGroupsClusterMemberId.class.getName());
        store2.config().setSlotKeyGeneratorClassName(JGroupsClusterMemberId.class.getName());

        store1.start();
        Thread.sleep(1000);
        store2.start();
        Thread.sleep(2000);

        // Write data to multiple slots through store1
        byte[] data1 = "slot-data-1".getBytes();
        byte[] data2 = "slot-data-2".getBytes();
        byte[] data3 = "slot-data-3".getBytes();

        store1.slots().write(0, data1, true);
        store1.slots().write(1, data2, true);
        store1.slots().write(2, data3, true);

        Thread.sleep(2000); // Give time for replication

        // Verify all slots are consistent across both stores
        Assertions.assertArrayEquals(data1, store2.slots().read(0));
        Assertions.assertArrayEquals(data2, store2.slots().read(1));
        Assertions.assertArrayEquals(data3, store2.slots().read(2));

        // Write through store2 and verify it replicates to store1
        byte[] data4 = "slot-data-4".getBytes();
        store2.slots().write(3, data4, true);
        Thread.sleep(1000);

        Assertions.assertArrayEquals(data4, store1.slots().read(3));

        // Clean up
        for (int i = 0; i < 4; i++) {
            store1.slots().clear(i, true);
        }

        store1.stop();
        store2.stop();
    }
}
