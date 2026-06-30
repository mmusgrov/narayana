package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.arjuna.common.CoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.CoreEnvironmentBeanException;
import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.exceptions.ObjectStoreException;
import com.arjuna.ats.arjuna.objectstore.RecoveryStore;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.arjuna.state.OutputObjectState;
import com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStoreAdaptor;
import com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStoreEnvironmentBean;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.ByteArrayKey;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsSlots;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsStoreEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import org.jgroups.blocks.ReplCache;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;

public class JGroupsSlotsTest {
    public static void setupStore() throws IOException, CoreEnvironmentBeanException {
        // common config for each slot store
        SlotStoreEnvironmentBean slotStoreConfig = BeanPopulator.getDefaultInstance(SlotStoreEnvironmentBean.class);
        JGroupsStoreEnvironmentBean config = BeanPopulator.getDefaultInstance(JGroupsStoreEnvironmentBean.class);
        BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class).setNodeIdentifier("1");
        BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).setObjectStoreType(SlotStoreAdaptor.class.getName());
        var slots = new JGroupsSlots(); // slot store backed by an infinispan cache

        slotStoreConfig.setBackingSlotsClassName(JGroupsSlots.class.getName());

        config.setNumberOfSlots(slotStoreConfig.getNumberOfSlots());
        config.setBytesPerSlot(slotStoreConfig.getBytesPerSlot());
        config.setStoreDir(slotStoreConfig.getStoreDir());
        config.setSyncWrites(true);
        config.setSyncDeletes(true);
        config.setNodeAddress("node1");
        config.setCacheName("replCache");
        config.setBackingSlots(slots);

        slots.init(config); // can throw IOException

        // tell the recovery manager that we are using the slot store (note beans can only be set once)
        BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).
                setObjectStoreType(SlotStoreAdaptor.class.getName());
        BeanPopulator.setBeanInstanceIfAbsent(JGroupsStoreEnvironmentBean.class.getName(), config);
    }

    @Test
    public void testIsSane() throws Exception {
        ReplCache<ByteArrayKey, byte[]> cache = new ReplCache<>("jgroups-transport-config.xml", "xxx");
        cache.setCallTimeout(1500L);
        cache.setCachingTime(30000L);
        cache.setMigrateData(true);
        cache.start();

        try {
            byte[] k = "key-repl-1".getBytes();
            byte[] v = "value-repl-1".getBytes();
            ByteArrayKey key = new ByteArrayKey(k);

            cache.put(key, v, (short) 1, 0);
            byte[] bytes = cache.get(key);
            Assertions.assertArrayEquals(v, bytes);

            JGroupsStoreEnvironmentBean config = BeanPopulator.getDefaultInstance(JGroupsStoreEnvironmentBean.class);
            ReplCache<ByteArrayKey, byte[]> slotStoreCache = config.getCache();

            slotStoreCache.put(key, v, (short) 1, 0);
            bytes = cache.get(key);
            Assertions.assertArrayEquals(v, bytes);

            slotStoreCache.stop();
        } finally {
            cache.stop();
        }
    }

    @Test
    public void test() throws IOException, CoreEnvironmentBeanException {
        setupStore();

        SlotStoreEnvironmentBean slotStoreConfig = BeanPopulator.getDefaultInstance(SlotStoreEnvironmentBean.class);
        String backingSlotsClassName = slotStoreConfig.getBackingSlotsClassName();
        Assertions.assertEquals(JGroupsSlots.class.getName(), backingSlotsClassName);

        RecoveryStore recoveryStore = StoreManager.getRecoveryStore();

        String data = "junit1";
        String typeName = "StateManager/junit1";
        OutputObjectState oos = new OutputObjectState();

        oos.packString(data);
        Uid uid = new Uid();

        try {
            Assertions.assertTrue(recoveryStore.write_committed(uid, typeName, oos));
            // Give time for message to propagate
            Thread.sleep(500);
            InputObjectState inputData = recoveryStore.read_committed(uid, typeName);
            String tn = inputData.unpackString();
            Assertions.assertEquals(data, tn);
        } catch (ObjectStoreException e) {
            Assertions.fail(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
