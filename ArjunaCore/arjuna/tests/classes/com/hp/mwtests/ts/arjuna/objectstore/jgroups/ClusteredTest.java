package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.arjuna.common.CoreEnvironmentBean;
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

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class ClusteredTest {

    private static URLClassLoader createIsolatedClassLoader() throws Exception {
        // Get current classpath
        String classpath = System.getProperty("java.class.path");
        String[] paths = classpath.split(File.pathSeparator);

        URL[] urls = new URL[paths.length];
        for (int i = 0; i < paths.length; i++) {
            File file = new File(paths[i]);
            urls[i] = file.toURI().toURL();
        }

        // Create new class loader with null parent to ensure isolation
        // (using system class loader as parent to load JDK classes)
        return new URLClassLoader(urls, ClassLoader.getSystemClassLoader().getParent());
    }

    static class IsolatedSlotStoreAdaptor {
        private final static String SLOT_STORE_ADAPTOR_CLASSNAME =
                "com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStoreAdaptor";
        private final ClassLoader classLoader;
        private final Object adaptorInstance;
        private final Class<?> adaptorClass;
        private final SlotStoreEnvironmentBean config;

        IsolatedSlotStoreAdaptor() throws Exception {
            // Create isolated class loader
            this.classLoader = createIsolatedClassLoader();

            // Load ReplCache class in the isolated class loader
            this.adaptorClass = classLoader.loadClass(SLOT_STORE_ADAPTOR_CLASSNAME);
            Class<?> beanClass = classLoader.loadClass(SlotStoreEnvironmentBean.class.getName());
            this.config = (SlotStoreEnvironmentBean) beanClass.getDeclaredConstructor().newInstance();

            // Create instance: new SlotStoreAdaptor(SlotStoreEnvironmentBean)
//            this.config = BeanPopulator.getDefaultInstance(SlotStoreEnvironmentBean.class);
//            ObjectStoreEnvironmentBean storeEnvBean = BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, name);
            String storeType = SlotStoreAdaptor.class.getName();//storeEnvBean.getObjectStoreType();
//            this.adaptorInstance = ClassloadingUtility.loadAndInstantiateClass(
//                    ObjectStoreAPI.class, storeType, null, true);

            this.adaptorInstance = adaptorClass
                    .getConstructor(SlotStoreEnvironmentBean.class)
                    .newInstance(config);
/*            String environmentBeanInstanceName = null;
            Constructor[] ctors = adaptorClass.getConstructors();
            Class environmentBeanClass = null;
            Object value = null;
            for(Constructor constructor : ctors) {
                if(constructor.getParameterCount() == 1 &&
                        constructor.getParameterTypes()[0].getCanonicalName().endsWith("EnvironmentBean")) {
                    environmentBeanClass = constructor.getParameterTypes()[0];
                    value = constructor.newInstance(config);
                    break;
                }
            }
            this.adaptorInstance = value;*/
        }

        ClassLoader getClassLoader() {
            return classLoader; // for debugging
        }

        // ObjectStoreAPI methods:

        boolean commit_state(Uid u, String tn) throws Exception {
            Method method = adaptorClass.getMethod("commit_state", Uid.class, String.class);
            return (boolean) method.invoke(adaptorInstance, u, tn);
        }

        boolean allObjUids(String s, InputObjectState buff, int m) throws Exception {
            Method method = adaptorClass.getMethod("allObjUids", String.class, InputObjectState.class, int.class);
            return (boolean) method.invoke(adaptorInstance, s, buff, m);
        }

        boolean allObjUids(String s, InputObjectState buff) throws Exception {
            Method method = adaptorClass.getMethod("allObjUids", String.class, InputObjectState.class);
            return (boolean) method.invoke(adaptorInstance, s, buff);
        }

        public boolean allTypes(InputObjectState buff) throws Exception {
            Method method = adaptorClass.getMethod("allTypes", InputObjectState.class);
            return (boolean) method.invoke(adaptorInstance, buff);
        }

        public int currentState(Uid u, String tn) throws Exception {
            Method method = adaptorClass.getMethod("currentState", Uid.class, String.class);
            return (int) method.invoke(adaptorInstance, u, tn);
        }

        public boolean hide_state(Uid u, String tn) throws Exception {
            Method method = adaptorClass.getMethod("hide_state", Uid.class, String.class);
            return (boolean) method.invoke(adaptorInstance, u, tn);
        }

        public boolean reveal_state(Uid u, String tn) throws Exception {
            Method method = adaptorClass.getMethod("reveal_state", Uid.class, String.class);
            return (boolean) method.invoke(adaptorInstance, u, tn);
        }

        public InputObjectState read_committed(Uid u, String tn) throws Exception {
            Method method = adaptorClass.getMethod("read_committed", Uid.class, String.class);
            return (InputObjectState) method.invoke(adaptorInstance, u, tn);
        }

        public boolean isType(Uid u, String tn, int st) throws Exception {
            Method method = adaptorClass.getMethod("isType", Uid.class, String.class, int.class);
            return (boolean) method.invoke(adaptorInstance, u, tn, st);
        }

        public InputObjectState read_uncommitted(Uid u, String tn) throws Exception {
            Method method = adaptorClass.getMethod("read_uncommitted", Uid.class, String.class);
            return (InputObjectState) method.invoke(adaptorInstance, u, tn);
        }

        public boolean remove_uncommitted(Uid u, String tn) throws Exception {
            Method method = adaptorClass.getMethod("remove_uncommitted", Uid.class, String.class);
            return (boolean) method.invoke(adaptorInstance, u, tn);
        }

        public boolean write_uncommitted(Uid u, String tn, OutputObjectState buff) throws Exception {
            Method method = adaptorClass.getMethod("write_uncommitted", Uid.class, String.class, OutputObjectState.class);
            return (boolean) method.invoke(adaptorInstance, u, tn, buff);
        }

        public boolean fullCommitNeeded() throws Exception {
            Method method = adaptorClass.getMethod("fullCommitNeeded");
            return (boolean) method.invoke(adaptorInstance);
        }

        public boolean remove_committed(Uid u, String tn) throws Exception {
            Method method = adaptorClass.getMethod("remove_uncommitted", Uid.class, String.class);
            return (boolean) method.invoke(adaptorInstance, u, tn);
        }

        public boolean write_committed(Uid u, String tn, OutputObjectState buff) throws Exception {
            Method method = adaptorClass.getMethod("write_committed", Uid.class, String.class, OutputObjectState.class);
            return (boolean) method.invoke(adaptorInstance, u, tn, buff);
        }

        public void sync() throws Exception {
            Method method = adaptorClass.getMethod("sync");
            method.invoke(adaptorInstance);
        }

        public String getStoreName() throws Exception {
            Method method = adaptorClass.getMethod("getStoreName");
            return (String) method.invoke(adaptorInstance);
        }

        public void start() throws Exception {
            Method method = adaptorClass.getMethod("start");
            method.invoke(adaptorInstance);
        }

        public void stop() throws Exception {
            Method method = adaptorClass.getMethod("stop");
            method.invoke(adaptorInstance);
        }
    }

    private IsolatedSlotStoreAdaptor adaptor1;
    private IsolatedSlotStoreAdaptor adaptor2;

    public void setupStore() throws Exception {
        adaptor1 = new IsolatedSlotStoreAdaptor();
        adaptor2 = new IsolatedSlotStoreAdaptor();

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

        // create two slot store adaptors in separate class loaders
//TODO

        slots.init(config); // can throw IOException

        // tell the recovery manager that we are using the slot store (note beans can only be set once)
        BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).
                setObjectStoreType(SlotStoreAdaptor.class.getName());
        BeanPopulator.setBeanInstanceIfAbsent(JGroupsStoreEnvironmentBean.class.getName(), config);
    }

    private JGroupsIsolatedCache createCache(JGroupsStoreEnvironmentBean config) throws Exception {
        JGroupsIsolatedCache cache = new JGroupsIsolatedCache(config.getJGroupsConfigFileName(), config.getCacheName());

        cache.setCallTimeout(1500L);
        cache.setCachingTime(30000L);

        return cache;
    }

//    @Test
    public void testIsSane() throws Exception {
        ReplCache<ByteArrayKey, byte[]> cache = new ReplCache<>("jgroups-transport-config.xml", "xxx");
        cache.setCallTimeout(1500L);
        cache.setCachingTime(30000L);
        cache.setMigrateData(true);
        cache.start();

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
    }

    @Test
    public void test() throws Exception {
        setupStore();

        SlotStoreEnvironmentBean slotStoreConfig = BeanPopulator.getDefaultInstance(SlotStoreEnvironmentBean.class);
        String backingSlotsClassName = slotStoreConfig.getBackingSlotsClassName();
        Assertions.assertEquals(JGroupsSlots.class.getName(), backingSlotsClassName);

        JGroupsStoreEnvironmentBean config1 =
                BeanPopulator.getDefaultInstance(JGroupsStoreEnvironmentBean.class);
        JGroupsStoreEnvironmentBean config2 = new JGroupsStoreEnvironmentBean();
        JGroupsStoreEnvironmentBean config3 = new JGroupsStoreEnvironmentBean();
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
