/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.exceptions.ObjectStoreException;
import com.arjuna.ats.arjuna.objectstore.RecoveryStore;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.arjuna.state.OutputObjectState;
import com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStoreAdaptor;
import com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStoreEnvironmentBean;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsSlots;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import org.jgroups.JChannel;
import org.jgroups.blocks.ReplCache;
import org.jgroups.blocks.ReplicatedHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsStoreEnvironmentBean;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class JGroupsClusterTest {
    // a name for the cluster
    private final String CLUSTER1 = "objectStoreCluster1";
    private final String CLUSTER2 = "objectStoreCluster2";
    // which will be sharing the same object store
    private final String OBJECT_STORE_NAME = "sharedStore";

    private List<JGroupsStoreEnvironmentBean> storeConfigs; // configs for each store in the cluster

    private ReplicatedHashMap<byte[], byte[]> map1 = null; // see ReplicatedHashMapTest
    private ReplicatedHashMap<byte[], byte[]> map2 = null;
    private ReplCache<byte[], byte[]> cache1 = null;
    private ReplCache<byte[], byte[]> cache2 = null;

    @BeforeEach
    public void beforeEach() throws Exception {
        String props="udp.xml";
        JChannel c1 = new JChannel(props);
        JChannel c2 = new JChannel(props);

        map1 = new ReplicatedHashMap<>(c1);
        map2 = new ReplicatedHashMap<>(c2);
        cache1 = new ReplCache<>(props, CLUSTER1);
        cache2 = new ReplCache<>(props, CLUSTER2);

        try {
            c1.connect(CLUSTER1);
            c2.connect(CLUSTER1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        map1.start(10000);
        map2.start(10000);
        cache1.start();
        cache2.start();
    }

    @AfterEach
    public void afterEach() {
        map1.clear();
        map2.clear();
        cache1.stop();
        cache2.stop();
    }

    @Test
    public void test1() throws Exception {
        map1.put("k1".getBytes(), "v1".getBytes());
        map2.put("k2".getBytes(), "v2".getBytes());
        assertEquals(map1.get("k1".getBytes()), map2.get("k2".getBytes()));
    }

    @Test
    public void test2() throws Exception {
        cache1.put("k1".getBytes(), "v1".getBytes());
        cache2.put("k2".getBytes(), "v2".getBytes());
        assertEquals(cache1.get("k1".getBytes()), cache2.get("k2".getBytes()));
    }

    @Test
    public void test3() throws Exception {
        var config = new JGroupsStoreEnvironmentBean();
        var slots = new JGroupsSlots(); // slot store backed by a JGroups cache
//        ReplCache<byte[], byte[]> cache = config.getCache();

//        config.setCache(cache);
        config.setBackingSlots(slots);

        slots.init(config);

        // tell the recovery manager that we are using the slot store
        BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).
                setObjectStoreType(SlotStoreAdaptor.class.getName());
        RecoveryStore recoveryStore = startRecoveryStore(config);
        String data = "junit1";
        String typeName = "StateManager/junit1";
        OutputObjectState oos = new OutputObjectState();
        oos.packString(data);

        try {
            Uid uid = new Uid();
            Assertions.assertTrue(recoveryStore.write_committed(uid, typeName, oos));
            // Thread.sleep(10); // the ReplicatedHashMap is not synchronous, go back to ReplCache
            InputObjectState inputData = recoveryStore.read_committed(uid, typeName);
            String tn = inputData.unpackString();
            assertEquals(data, tn);
        } catch (ObjectStoreException e) {
            fail(e);
        }
    }

    private RecoveryStore startRecoveryStore(SlotStoreEnvironmentBean bean) {
        StoreManager.shutdown(); // remove any existing store

        try {
            /*
             * The intent is to have one recovery store per JVM, and we want to start each with a different config.
             * However, environment bean instances are global to the JVM and can only be set once so replace the current
             * bean using MethodHandles to update the BeanPopulator bean instances map (an alternative could be
             * to update all the fields of the existing bean instance).
             */
            replaceEnvironmentBean(bean);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        return StoreManager.getRecoveryStore();
    }

    // update the slot store environment bean
    private void replaceEnvironmentBean(SlotStoreEnvironmentBean bean) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                BeanPopulator.class,
                MethodHandles.lookup()
        );

        // Get a VarHandle for the private static field
        VarHandle varHandle = lookup.findStaticVarHandle(
                BeanPopulator.class,
                "beanInstances",
                ConcurrentMap.class
        );

        ConcurrentMap<String, Object> beanInstances = (ConcurrentMap<String, Object>) varHandle.get();

        beanInstances.put(SlotStoreEnvironmentBean.class.getName(), bean);
    }

    /*    *//**
     * start a new recovery manager, shutting down the current one if it is running
     * @param bean config for the recovery manager
     * @return the new store
     *//*
    private RecoveryStore startRecoveryStore(InfinispanStoreEnvironmentBean bean) {
        StoreManager.shutdown(); // remove any existing store

        try {
            *//*
             * The intent is to have one recovery store per JVM, and we want to start each with a different config.
             * However, environment bean instances are global to the JVM and can only be set once so replace the current
             * bean using MethodHandles to update the BeanPopulator bean instances map (an alternative could be
             * to update all the fields of the existing bean instance).
             *//*
            replaceEnvironmentBean(bean);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        return StoreManager.getRecoveryStore();
    }

    // update the slot store environment bean
    private void replaceEnvironmentBean(SlotStoreEnvironmentBean bean) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                BeanPopulator.class,
                MethodHandles.lookup()
        );

        // Get a VarHandle for the private static field
        VarHandle varHandle = lookup.findStaticVarHandle(
                BeanPopulator.class,
                "beanInstances",
                ConcurrentMap.class
        );

        ConcurrentMap<String, Object> beanInstances = (ConcurrentMap<String, Object>) varHandle.get();

        beanInstances.put(SlotStoreEnvironmentBean.class.getName(), bean);
    }

    // verify that a value written to the store can be read back correctly
    @Test
    public void test1 () throws Exception {
        class RecordHolder {
            final String typeName;
            final Uid uid;
            final OutputObjectState outputObjectState;
            final String data;
            final boolean typeFound;
            final boolean uidFound;

            public RecordHolder(String typeName, String data) throws IOException {
                this.typeName = typeName;
                this.uid = new Uid();
                this.outputObjectState = new OutputObjectState();
                this.data = data;
                this.typeFound = false;
                this.uidFound = false;

                this.outputObjectState.packString(data);
            }
        }
        RecordHolder[] records = {
                new RecordHolder("StateManager/junit1", "hello1"),
                new RecordHolder("StateManager/junit2", "hello2")
        };

        RecoveryStore recoveryStore = startRecoveryStore(storeConfigs.get(0));

        // write the records to the store and read them back
        for (RecordHolder record: records) {
            // add the record and read it back again
            Assertions.assertTrue(recoveryStore.write_committed(record.uid, record.typeName, record.outputObjectState));
            InputObjectState inputData = recoveryStore.read_committed(record.uid, record.typeName);

            assertEquals(record.data, inputData.unpackString());
        }

        // and verify that the types are present in the store
        Collection<String> types = getAllTypes(recoveryStore);
        for (RecordHolder record: records) {
            Assertions.assertTrue(types.contains(record.typeName));
        }

        // and finally verify that the record was replicated to the other stores
        for (int i = 1; i < storeConfigs.size(); i++) {
            recoveryStore = startRecoveryStore(storeConfigs.get(i));

            for (RecordHolder record : records) {
                InputObjectState inputData = recoveryStore.read_committed(record.uid, record.typeName);
                String datum = inputData.unpackString();

                assertEquals(record.data, datum);
            }
        }

        // clean up
        for (RecordHolder record : records) {
            Assertions.assertTrue(recoveryStore.remove_committed(record.uid, record.typeName));
        }
    }

    // verify that data written to one cluster node can be read back from other nodes even when the original node is down
    @Test
    public void testCrash() {
        RecoveryStore recoveryStore = startRecoveryStore(storeConfigs.get(0));
        AtomicAction A = new AtomicAction();

        A.begin();

        A.add(new CrashRecord(CrashRecord.CrashLocation.NoCrash, CrashRecord.CrashType.Normal));
        A.add(new CrashRecord(CrashRecord.CrashLocation.CrashInCommit, CrashRecord.CrashType.HeuristicHazard));

        int outcome = A.commit();

        assertEquals(ActionStatus.H_HAZARD, outcome);

        try {
            boolean exists = containsAtomicAction(recoveryStore, A);

            Assertions.assertTrue(exists);
        } catch (Exception e) {
            fail(e);
        }

        // stop all caches (simulates failure of the cluster) to verify that removal of logs will fail
        for (EmbeddedCacheManager m : cacheManagers) {
            m.stop();
        }

        // should still be able to read the types when the cache is unavailable - see SlotStore.getMatchingKeys();
        boolean exists = containsAtomicAction(recoveryStore, A);
        Assertions.assertTrue(exists);

        try {
            // but should not be able to read and write the cache:
            recoveryStore.remove_committed(A.getSavingUid(), A.type());
            fail("should not be able to access a TERMINATED cache");
        } catch (ObjectStoreException expected) {
            // should be ISPN000323 which indicates that the cache is in the TERMINATED state
        }
    }

    private boolean containsAtomicAction(RecoveryStore recoveryStore, AtomicAction aa) {
        InputObjectState ios = new InputObjectState();

        try {
            if (recoveryStore.allObjUids(aa.type(), ios, StateStatus.OS_UNKNOWN)) {
                Uid id;

                do {
                    try {
                        id = UidHelper.unpackFrom(ios);
                        if (id.equals(aa.get_uid())) {
                            return true;
                        }
                    } catch (Exception ex) {
                        return false;
                    }
                }
                while (id.notEquals(Uid.nullUid()));
            }
        } catch (ObjectStoreException ignore) {
        }

        return false;
    }

    private Collection<String> getAllTypes(RecoveryStore store) throws Exception {
        Collection<String> allTypes = new ArrayList<>();
        InputObjectState types = new InputObjectState();
        boolean hasTypes = store.allTypes(types);

        Assertions.assertTrue(hasTypes);

        while (true) {
            try {
                String typeName = types.unpackString();
                assertNotNull(typeName);
                if (typeName.isEmpty())
                    break;
                allTypes.add(typeName);
            } catch (IOException e1) {
                break;
            }
        }

        return allTypes;
    }

    *//*
     * simple sanity check to verify that key/value pairs are replicated correctly
     * and are still available when one node fails and are still available from the
     * failed node on restart
     *//*
    @Test
    public void testCacheReplicationIsSane() {
        // Get cache from each node
        Cache<byte[], byte[]> cache0 = storeConfigs.get(0).getCache();
        Cache<byte[], byte[]> cache1 = storeConfigs.get(1).getCache();
        Cache<byte[], byte[]> cache2 = storeConfigs.get(2).getCache();

        byte[] k1 = "k1".getBytes();
        byte[] k2 = "k2".getBytes();
        byte[] v1 = "v1".getBytes();
        byte[] v2 = "v2".getBytes();

        // put data in node 0
        cache0.put(k1, v1);

        // Verify it's replicated to nodes 1 and 2
        Assertions.assertArrayEquals(v1, cache1.get(k1));
        Assertions.assertArrayEquals(v1, cache2.get(k1));

        // put data in node 1
        cache1.put(k2, v2);

        // verify it's replicated to nodes 0 and 2
        Assertions.assertArrayEquals(v2, cache0.get(k2));
        Assertions.assertArrayEquals(v2, cache2.get(k2));

        // verify all caches have the same size
        assertEquals(2, cache0.size());
        assertEquals(2, cache1.size());
        assertEquals(2, cache2.size());

        // simulate node 0 failure by stopping the manager
        cacheManagers.get(0).stop();
        // and verify that the value is still replicated at nodes 1 and 2
        Assertions.assertArrayEquals(v2, cache1.get(k2));
        Assertions.assertArrayEquals(v2, cache2.get(k2));

        // restart (ie recreate) the "failed" node 0
        var manager = createCacheManager(cacheManagers.get(0).getNodeAddress());
        cacheManagers.set(0, manager);
        // and restart cache 0
        cache0 = manager.getCache(storeConfigs.get(0).getCacheName());
        cache0.start();

        cache0.clear();
        cache1.clear();
        cache2.clear();
    }

    *//*
     * validate that an attempt to initialise a slot store with insufficient capacity will fail
     *//*
    @Test
    public void misconfigureBackingSlots() throws IOException {
        int SLOT_COUNT = 1;
        String CACHE_NAME = "simple";
        Cache<byte[], byte[]> cache;
        InfinispanStoreEnvironmentBean config;
        InfinispanSlots slots = new InfinispanSlots(); // slot store backed by an infinispan cache

        byte[] k1 = "k1".getBytes();
        byte[] k2 = "k2".getBytes();
        byte[] v1 = "v1".getBytes();
        byte[] v2 = "v2".getBytes();

        config = new InfinispanStoreEnvironmentBean();

        try (DefaultCacheManager manager = new DefaultCacheManager()) {
            manager.defineConfiguration(CACHE_NAME, new ConfigurationBuilder().build());
            cache = manager.getCache(CACHE_NAME);

            cache.put(k1, v1);
            cache.put(k2, v2);

            config.setNumberOfSlots(SLOT_COUNT);
            config.setCacheName(CACHE_NAME);
            config.setCache(manager.getCache(CACHE_NAME));
            config.setBackingSlots(slots);
            config.setSlotKeyGeneratorClassName(ClusterMemberId.class.getName());

            try {
                // initialise the slot store whose capacity is 1 (SLOT_COUNT) with a cache (cache) containing 2 entries
                slots.init(config);
                fail("should not be able to initialise a slot store with more entries than it has capacity for");
            } catch (IOException e) {
                String expected = tsLogger.i18NLogger.get_infinispan_too_few_slots(cache.size(), SLOT_COUNT);
                Assertions.assertTrue(e.getMessage().endsWith(expected));
            }
        }
    }

    *//*
     * test that writing to a full store will fail
     *//*
    @Test
    public void backingSlotsCapacityReachedTest () throws IOException {
        int SLOT_COUNT = 1; // to test writing to a full store
        String CACHE_NAME = "simple";
        InfinispanStoreEnvironmentBean config = new InfinispanStoreEnvironmentBean();
        InfinispanSlots slots = new InfinispanSlots(); // slot store backed by an infinispan cache

        // tell the recovery manager that we are using the slot store
        BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).
                setObjectStoreType(SlotStoreAdaptor.class.getName());

        try (DefaultCacheManager manager = new DefaultCacheManager()) {
            manager.defineConfiguration(CACHE_NAME, new ConfigurationBuilder().build());

            config.setNumberOfSlots(SLOT_COUNT);
            config.setCacheName(CACHE_NAME);
            config.setCache(manager.getCache(CACHE_NAME));
            config.setBackingSlots(slots);
            config.setSlotKeyGeneratorClassName(ClusterMemberId.class.getName());

            slots.init(config);

            RecoveryStore recoveryStore = startRecoveryStore(config);
            OutputObjectState oos = new OutputObjectState();
            oos.packString("junit1");

            try {
                // the store capacity is 1 so the first write should succeed
                Assertions.assertTrue(recoveryStore.write_committed(new Uid(), "StateManager/junit1", oos));
            } catch (ObjectStoreException e) {
                fail(e);
            }
            try {
                // The store capacity is 1 so writing a second record should fail
                Assertions.assertFalse(recoveryStore.write_committed(new Uid(), "StateManager/junit2", oos));
            } catch (ObjectStoreException e) {
                fail("writing to full store should return false but an exception was thrown: " + e.getMessage());
            }
        }
    }*/
}
