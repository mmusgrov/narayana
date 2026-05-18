package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Helper class that wraps a ReplCache instance loaded in an isolated class loader
 */
class JGroupsIsolatedCache {
    private final ClassLoader classLoader;
    private final Object cacheInstance;
    final Class<?> cacheClass;

    JGroupsIsolatedCache(String props, String clusterName) throws Exception {
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
