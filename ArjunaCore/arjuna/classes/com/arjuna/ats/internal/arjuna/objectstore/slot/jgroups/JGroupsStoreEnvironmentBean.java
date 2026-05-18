/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups;

import com.arjuna.ats.arjuna.common.CoreEnvironmentBeanException;
import com.arjuna.ats.arjuna.logging.tsLogger;
import com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStoreEnvironmentBean;
import com.arjuna.common.internal.util.ClassloadingUtility;
import org.jgroups.blocks.ReplCache;

import java.io.File;

/**
 * Configuration properties for an JGroups backed slot store implementation
 * {@link com.arjuna.ats.internal.arjuna.objectstore.slot.BackingSlots}
 * <p>
 * NOTE: This is an Experimental feature (JGroups Slot Store) and is not recommended for production systems and may
 * contain breaking changes in future releases.
 */
public class JGroupsStoreEnvironmentBean extends SlotStoreEnvironmentBean implements JGroupsStoreEnvironmentBeanMBean {

    private String jGroupsConfigFileName = "jgroups-transport-config.xml";
    private ReplCache<ByteArrayKey, byte[]> cache;
    private String cacheName = "defaultJGroupsCache";
    private short replicationCount = -1;
    private boolean ignoreReturnValues = true;
    private String nodeAddress;
    private String groupName = null;
    private String slotKeyGeneratorClassName;
    private JGroupsSlotKeyGenerator jGroupsSlotKeyGenerator;

    /**
     * get the jGroups xml based cache configuration
     * <p>
     * Note that any properties set in this jGroups xml config file will override any related config in this bean
     * including:
     * {@link JGroupsStoreEnvironmentBean#getCacheName()}
     * {@link JGroupsStoreEnvironmentBean#getNodeAddress()}
     * {@link JGroupsStoreEnvironmentBean#getStoreDir()}
     * {@link JGroupsStoreEnvironmentBean#getGroupName()}
     * <p>
     * @return the name of config file
     */
    public String getJGroupsConfigFileName() {
        return jGroupsConfigFileName;
    }

    public void setJGroupsConfigFileName(String jGroupsConfigFileName) {
        this.jGroupsConfigFileName = jGroupsConfigFileName;
    }

    public ReplCache<ByteArrayKey, byte[]> getCache() throws CoreEnvironmentBeanException {
        if (cache == null) {
            if (jGroupsConfigFileName == null) {
                throw new CoreEnvironmentBeanException(tsLogger.i18NLogger.warn_jgroups_config());
            }

            cache = new ReplCache<>(jGroupsConfigFileName, getCacheName());
            cache.setCallTimeout(1500L);
            cache.setCachingTime(30000L);
            cache.setMigrateData(true);
        }

        return cache;
    }
        /*            if (jGroupsConfigFileName != null) {
                try {
                    DefaultCacheManager cacheManager = new DefaultCacheManager(
                            JGroupsStoreEnvironmentBean.class.getResourceAsStream(jGroupsConfigFileName));
                    if (cacheName == null) {
                        cacheName = cacheManager.getName(); // cache name defaults the cache manager name
                    }
                    nodeAddress = cacheManager.getNodeAddress();

                    cache = cacheManager.getCache(cacheName);
                } catch (IOException e) {
                    tsLogger.i18NLogger.warn_jgroups_config(e);
                    throw new RuntimeException(e);
                }
            }*/

    public void setCache(ReplCache<ByteArrayKey, byte[]> cache) {
        this.cache = cache;
    }

    /**
     * Define how many times an element should be available in a cluster.
     * The default is -1 meaning the element is stored on all cluster nodes (full replication).
     * With 1 the element is stored on a single node only, determined through consistent hashing (distribution).
     * Setting it to a number K greater than 1 will store the element K times in the cluster.
     * TODO implement the value internally by monitoring the cluster
     */
    public short getReplicationCount() {
        return replicationCount;
    }

    public void setReplicationCount(short replicationCount) {
        this.replicationCount = replicationCount;
    }

    /**
     * If the return value of write operations will be ignored.
     * The default is true to avoid needless remote calls
     *
     * @return true if return values are ignored
     */
    public boolean isIgnoreReturnValues() {
        return ignoreReturnValues;
    }

    public void setIgnoreReturnValues(boolean ignoreReturnValues) {
        this.ignoreReturnValues = ignoreReturnValues;
    }

    /**
     * The address of the node within a cluster.
     * Used in clustered and embedded scenarios to identify which member of the JGroups cluster
     * the JVM is currently representing.
     *
     * @return the node address
     */
    public String getNodeAddress() {
        return nodeAddress;
    }

    public void setNodeAddress(String nodeAddress) {
        this.nodeAddress = nodeAddress;
    }

    /**
     * the name of the cache used to store the key/value pairs for this slot store
     *
     * @return the name of the replicated cache
     */
    public String getCacheName() {
        return cache != null ? cache.getClusterName() : cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    /**
     * Get the cache backup location.
     * @return when write through caching is configured return the filesystem location used for the storage
     */
    @Override
    public String getStoreDir() {
        return super.getStoreDir();
    }

    /**
     * set the location of the storage for write through caches
     * WARNING this setting only applies for programmatic configuration of the cache, ie if
     * {@link JGroupsStoreEnvironmentBean#getJGroupsConfigFileName()} is configured then the cache config
     * will use that file instead
     * @param storeDir the path to the store directory. If storeDir is not an absolute path then it will be
     *                 interpreted as being relative to the User's current working directory
     *                 (as defined by the user.dir system property)
     */
    @Override
    public void setStoreDir(String storeDir) {
        if (storeDir != null) {
            if (!storeDir.startsWith(String.valueOf(File.separatorChar))) {
                storeDir = System.getProperty("user.dir") + "/" + storeDir;
                super.setStoreDir(storeDir);
            }

            super.setStoreDir(storeDir);
        }
    }

    /**
     * Cluster Configuration Considerations
     * <p>
     * 1. A single member must run the recovery manager and a new one started if it fails (aka an HA singleton)
     * 2. Top down recovery via the AtomicActionRecoveryModule will recover all transactions in the store unless the
     *    recovery groupName is set in which case the recovery manager will only recover AtomicActions created
     *    with that groupName.
     * 3. Any member can create and commit transactions ({@link com.arjuna.ats.arjuna.recovery.TransactionStatusManager}
     *    will be used during recovery to decide if the creator is still running)
     * <p>
     * The group name may also be used in Distributed Mode which can provide orders of magnitude improvements in
     * scalability than can the Replication Mode. For example if a large cluster is supporting multi-tenancy
     * (multiple transaction and recovery managers) then Distributed Mode together with key grouping can ensure
     * that cache entries for a particular group are co-located on a group of cluster nodes rather than being
     * scattered over the entire cluster.
     *
     * @return the group name or null if not required
     */
    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    @Override
    public void setBackingSlots(JGroupsSlots jGroupsSlots) {
        super.setBackingSlots(jGroupsSlots);
    }

    /**
     * classname of the generator function for internal slot keys. If the classname is unset and
     * {@link JGroupsStoreEnvironmentBean#setSlotKeyGenerator(JGroupsSlotKeyGenerator)} has not been called
     * then a generator based on {@link com.arjuna.ats.arjuna.common.Uid} will be used
     */
    public void setSlotKeyGeneratorClassName(String slotKeyGeneratorClassName) {
        this.slotKeyGeneratorClassName = slotKeyGeneratorClassName;
    }

    public String getSlotKeyGeneratorClassName() {
        return slotKeyGeneratorClassName;
    }

    /**
     * Define a strategy for initialising slot store keys. If unset then a
     * {@link com.arjuna.ats.arjuna.common.Uid} will be used
     * <p>
     * @param jGroupsSlotKeyGenerator the slot key generator
     */
    public void setSlotKeyGenerator(JGroupsSlotKeyGenerator jGroupsSlotKeyGenerator) {
        this.jGroupsSlotKeyGenerator = jGroupsSlotKeyGenerator;
    }

    public JGroupsSlotKeyGenerator getSlotKeyGenerator() {
        if(jGroupsSlotKeyGenerator == null && slotKeyGeneratorClassName != null)
        {
            synchronized (this) {
                if(jGroupsSlotKeyGenerator == null && (slotKeyGeneratorClassName != null && !slotKeyGeneratorClassName.isBlank())) {
                    jGroupsSlotKeyGenerator = ClassloadingUtility.loadAndInstantiateClass(JGroupsSlotKeyGenerator.class, slotKeyGeneratorClassName, null);
                }
            }
        }

        return jGroupsSlotKeyGenerator;
    }
/*
    public ReplicatedHashMap<byte[], byte[]> getCache2() throws Exception {
        if (cache2 == null) {
            JChannel channel = new JChannel(getJGroupsConfigFileName());
            channel.connect("cluster");//TODO getCacheName());

            cache2 = new ReplicatedHashMap<>(channel);
        }

        return cache2;
    }*/
}
