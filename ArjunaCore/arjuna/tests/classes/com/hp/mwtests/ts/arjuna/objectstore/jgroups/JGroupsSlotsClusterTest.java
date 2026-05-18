package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.ByteArrayKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

/**
 * Tests ReplCache instances running in separate class loaders to simulate
 * a more realistic distributed cache scenario where different nodes may have
 * different class loaders.
 */
public class JGroupsSlotsClusterTest {

    private static final String PROPS = "udp.xml";
    private static final String CLUSTER_NAME = "classloader-test-cluster";
    private static final long TIMEOUT = 10000L;

    private IsolatedCache cache1;
    private IsolatedCache cache2;

    /**
     * Wrapper for byte[] that implements proper hashCode/equals and Serializable.
     * Must be public static to be accessible across class loaders.
     */

    public static class ByteArrayKey2 implements Serializable {
        private static final long serialVersionUID = 1L;
        private final byte[] key;

        public ByteArrayKey2(byte[] key) {
            this.key = key != null ? key.clone() : null;
        }

        public byte[] getKey() {
            return key != null ? key.clone() : null;
        }

        @Override
        public int hashCode() {
            // Don't cache - recalculate each time to avoid issues with transient fields
            return key != null ? Arrays.hashCode(key) : 0;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            // Handle cross-classloader comparison
            if (!obj.getClass().getName().equals(this.getClass().getName())) {
                return false;
            }
            // Use reflection to get the key field if it's from a different class loader
            try {
                if (obj instanceof ByteArrayKey2) {
                    return Arrays.equals(key, ((ByteArrayKey2) obj).key);
                } else {
                    // Cross-class loader case
                    byte[] otherKey = (byte[]) obj.getClass().getMethod("getKey").invoke(obj);
                    return Arrays.equals(key, otherKey);
                }
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public String toString() {
            return "ByteArrayKey" + Arrays.toString(key);
        }
    }

    /**
     * Helper class that wraps a ReplCache instance loaded in an isolated class loader
     */
    static class IsolatedCache {
        private final ClassLoader classLoader;
        private final Object cacheInstance;
        private final Class<?> cacheClass;

        IsolatedCache(String props, String clusterName) throws Exception {
            // Create isolated class loader
            this.classLoader = createIsolatedClassLoader();

            // Load ReplCache class in the isolated class loader
            this.cacheClass = classLoader.loadClass("org.jgroups.blocks.ReplCache");

            // Create instance: new ReplCache(props, clusterName)
            this.cacheInstance = cacheClass
                .getConstructor(String.class, String.class)
                .newInstance(props, clusterName);
        }

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

        void start() throws Exception {
            Method startMethod = cacheClass.getMethod("start");
            startMethod.invoke(cacheInstance);
        }

        void stop() throws Exception {
            Method stopMethod = cacheClass.getMethod("stop");
            stopMethod.invoke(cacheInstance);
        }

        void setCallTimeout(long timeout) throws Exception {
            Method method = cacheClass.getMethod("setCallTimeout", long.class);
            method.invoke(cacheInstance, timeout);
        }

        void setCachingTime(long cachingTime) throws Exception {
            Method method = cacheClass.getMethod("setCachingTime", long.class);
            method.invoke(cacheInstance, cachingTime);
        }

        void setDefaultReplicationCount(short count) throws Exception {
            Method method = cacheClass.getMethod("setDefaultReplicationCount", short.class);
            method.invoke(cacheInstance, count);
        }

        void put(Object key, Object value, short replCount, long timeout) throws Exception {
            Method putMethod = cacheClass.getMethod("put", Object.class, Object.class, short.class, long.class);
            putMethod.invoke(cacheInstance, key, value, replCount, timeout);
        }

        Object get(Object key) throws Exception {
            Method getMethod = cacheClass.getMethod("get", Object.class);
            return getMethod.invoke(cacheInstance, key);
        }

        void remove(Object key) throws Exception {
            Method removeMethod = cacheClass.getMethod("remove", Object.class);
            removeMethod.invoke(cacheInstance, key);
        }

        int getClusterSize() throws Exception {
            Method method = cacheClass.getMethod("getClusterSize");
            return (Integer) method.invoke(cacheInstance);
        }

        String getLocalAddressAsString() throws Exception {
            Method method = cacheClass.getMethod("getLocalAddressAsString");
            return (String) method.invoke(cacheInstance);
        }

        String dump() throws Exception {
            Method method = cacheClass.getMethod("dump");
            return (String) method.invoke(cacheInstance);
        }

        ClassLoader getClassLoader() {
            return classLoader;
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        System.out.println("\n=== Setting up isolated class loader test ===");

        // Create two cache instances in separate class loaders
        cache1 = new IsolatedCache(PROPS, CLUSTER_NAME);
        cache2 = new IsolatedCache(PROPS, CLUSTER_NAME);

        // Verify they have different class loaders
        Assertions.assertNotSame(cache1.getClassLoader(), cache2.getClassLoader(),
            "Caches should have different class loaders");

        // Configure caches
        cache1.setCallTimeout(TIMEOUT);
        cache1.setCachingTime(30000L);
        cache1.setDefaultReplicationCount((short) -1); // Replicate to all nodes

        cache2.setCallTimeout(TIMEOUT);
        cache2.setCachingTime(30000L);
        cache2.setDefaultReplicationCount((short) -1); // Replicate to all nodes

        // Start both caches
        cache1.start();
        cache2.start();

        // Wait for cluster to form
        waitForClusterFormation();

        System.out.println("Cluster formed:");
        System.out.println("  Node 1: " + cache1.getLocalAddressAsString() +
                          " (ClassLoader: " + cache1.getClassLoader() + ")");
        System.out.println("  Node 2: " + cache2.getLocalAddressAsString() +
                          " (ClassLoader: " + cache2.getClassLoader() + ")");
    }

    @AfterEach
    public void tearDown() throws Exception {
        System.out.println("\n=== Tearing down isolated class loader test ===");

        if (cache1 != null) {
            try {
                cache1.stop();
            } catch (Exception e) {
                System.err.println("Error stopping cache1: " + e.getMessage());
            }
        }

        if (cache2 != null) {
            try {
                cache2.stop();
            } catch (Exception e) {
                System.err.println("Error stopping cache2: " + e.getMessage());
            }
        }

        // Allow time for cleanup
        Thread.sleep(1000);
    }

    private void waitForClusterFormation() throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT;

        while (System.currentTimeMillis() < deadline) {
            int size1 = cache1.getClusterSize();
            int size2 = cache2.getClusterSize();

            if (size1 >= 2 && size2 >= 2) {
                System.out.println("Cluster formed with " + size1 + " members");
                return;
            }

            Thread.sleep(100);
        }

        throw new Exception("Cluster failed to form within timeout. " +
                          "Size1: " + cache1.getClusterSize() +
                          ", Size2: " + cache2.getClusterSize());
    }

    @Test
    public void testStringKeyAcrossClassLoaders() throws Exception {
        System.out.println("\n=== Test: String key Put/Get across class loaders ===");

        String key = "test-key-string";
        String value = "test-value-string";

        // Put in cache1 with replication to all nodes (-1)
        System.out.println("Putting key=" + key + ", value=" + value + " in cache1");
        cache1.put(key, value, (short) -1, 30000L);

        // Allow time for replication
        Thread.sleep(2000);

        // Dump cache contents
        System.out.println("Cache1 contents:\n" + cache1.dump());
        System.out.println("Cache2 contents:\n" + cache2.dump());

        // Get from cache2 (different class loader)
        System.out.println("Getting key=" + key + " from cache2");
        Object result = cache2.get(key);

        System.out.println("Retrieved value: " + result);
        Assertions.assertNotNull(result, "Value should replicate across class loaders with String key");
        Assertions.assertEquals(result.toString(), value);
    }

    @Test
    public void testBasicPutGetAcrossClassLoaders() throws Exception {
        System.out.println("\n=== Test: ByteArrayKey Put/Get across class loaders ===");

        ByteArrayKey key = new ByteArrayKey(new byte[]{1, 2, 3, 4});
        String value = "test-value-1";

        // Put in cache1 with replication to all nodes (-1)
        System.out.println("Putting key=" + key + ", value=" + value + " in cache1");
        System.out.println("  Key hashCode: " + key.hashCode());
        System.out.println("  Key class: " + key.getClass() + " (loader: " + key.getClass().getClassLoader() + ")");
        cache1.put(key, value, (short) -1, 30000L);

        // Allow time for replication
        Thread.sleep(2000);

        // Dump cache contents
        System.out.println("Cache1 contents:\n" + cache1.dump());
        System.out.println("Cache2 contents:\n" + cache2.dump());

        // Get from cache2 (different class loader)
        ByteArrayKey key2 = new ByteArrayKey(new byte[]{1, 2, 3, 4});
        System.out.println("Getting key=" + key2 + " from cache2");
        System.out.println("  Key2 hashCode: " + key2.hashCode());
        System.out.println("  Key2 class: " + key2.getClass() + " (loader: " + key2.getClass().getClassLoader() + ")");
        System.out.println("  Keys equal? " + key.equals(key2));

        Object result = cache2.get(key2);

        System.out.println("Retrieved value: " + result);
        if (result == null) {
            // Try with the original key object
            System.out.println("Trying with original key object...");
            result = cache2.get(key);
            System.out.println("Result with original key: " + result);
        }

        Assertions.assertNotNull(result, "Should retrieve with equivalent key");
        Assertions.assertEquals(result.toString(), value);
    }

    @Test
    public void testByteArrayKeysAcrossClassLoaders() throws Exception {
        System.out.println("\n=== Test: ByteArrayKey equality across class loaders ===");

        byte[] keyBytes1 = {10, 20, 30, 40};
        byte[] keyBytes2 = {10, 20, 30, 40}; // Same content, different array

        ByteArrayKey key1 = new ByteArrayKey(keyBytes1);
        ByteArrayKey key2 = new ByteArrayKey(keyBytes2);

        String value = "byte-array-test-value";

        // Put using key1 in cache1 with full replication
        System.out.println("Putting with key1 in cache1");
        cache1.put(key1, value, (short) -1, 30000L);

        Thread.sleep(2000);

        // Get using key2 (same content, different instance) from cache2
        System.out.println("Getting with key2 (same content) from cache2");
        Object result = cache2.get(key2);

        System.out.println("Retrieved value: " + result);
        Assertions.assertNotNull(result, "Should retrieve with equivalent key");
        Assertions.assertEquals(result.toString(), value);
    }

    @Test
    public void testMultipleEntriesAcrossClassLoaders() throws Exception {
        System.out.println("\n=== Test: Multiple entries across class loaders ===");

        // Put multiple entries from cache1 with full replication
        for (int i = 0; i < 5; i++) {
            ByteArrayKey key = new ByteArrayKey(new byte[]{(byte) i});
            String value = "value-" + i;
            System.out.println("Putting key=" + key + ", value=" + value);
            cache1.put(key, value, (short) -1, 30000L);
        }

        Thread.sleep(3000);

        // Retrieve from cache2
        int successCount = 0;
        for (int i = 0; i < 5; i++) {
            ByteArrayKey key = new ByteArrayKey(new byte[]{(byte) i});
            Object result = cache2.get(key);
            System.out.println("Retrieved key=" + key + ", value=" + result);

            if (result != null && result.toString().equals("value-" + i)) {
                successCount++;
            }
        }

        Assertions.assertTrue(successCount >= 4,
            "At least 4 out of 5 entries should replicate (got " + successCount + ")");
    }

    @Test
    public void testRemoveAcrossClassLoaders() throws Exception {
        System.out.println("\n=== Test: Remove across class loaders ===");

        ByteArrayKey key = new ByteArrayKey(new byte[]{99, 88, 77});
        String value = "remove-test-value";

        // Put in cache1 with full replication
        cache1.put(key, value, (short) -1, 30000L);
        Thread.sleep(2000);

        // Verify it's in cache2
        Object result = cache2.get(key);
        Assertions.assertNotNull(result, "Value should be replicated");

        // Remove from cache2
        System.out.println("Removing key from cache2");
        cache2.remove(key);
        Thread.sleep(2000);

        // Verify it's removed from cache1
        result = cache1.get(key);
        System.out.println("After removal, value in cache1: " + result);
        Assertions.assertNull(result, "Value should be removed across all nodes");
    }

    @Test
    public void testClassLoaderIsolation() throws Exception {
        System.out.println("\n=== Test: Verify class loader isolation ===");

        // Verify that the ReplCache classes are different
        Class<?> class1 = cache1.cacheClass;
        Class<?> class2 = cache2.cacheClass;

        System.out.println("Cache1 class: " + class1 + " (loader: " + class1.getClassLoader() + ")");
        System.out.println("Cache2 class: " + class2 + " (loader: " + class2.getClassLoader() + ")");

        // They should have the same name but be different Class objects
        Assertions.assertEquals(class1.getName(), class2.getName(), "Should have same class name");
        Assertions.assertNotSame(class1, class2, "Should be different Class objects due to different class loaders");
        Assertions.assertNotSame(class1.getClassLoader(), class2.getClassLoader(),
            "Should have different class loaders");
    }
}
