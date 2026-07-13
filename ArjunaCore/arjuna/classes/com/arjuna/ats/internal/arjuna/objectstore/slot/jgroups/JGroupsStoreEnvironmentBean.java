/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups;

import com.arjuna.ats.arjuna.common.CoreEnvironmentBeanException;
import com.arjuna.ats.arjuna.logging.tsLogger;
import com.arjuna.ats.internal.arjuna.objectstore.slot.BackingSlots;
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
    private volatile ReplCache<ByteArrayKey, byte[]> cache;
    private String cacheName = "defaultJGroupsCache";
    private short replicationCount = -1;
    private boolean ignoreReturnValues = true;
    private String nodeAddress;
    private String groupName = null;
    private String slotKeyGeneratorClassName;
    private JGroupsSlotKeyGenerator jGroupsSlotKeyGenerator;
    private long cachingTime = 0L;  // L2 cache time in millis (0 = disabled for consistency)

    // WAL (Write-Ahead Log) persistence for crash recovery
    private boolean walEnabled = true; // persist writes to a log before writing to the cache
    private volatile int walFileSize = 1024*1024*2; // 10MB per file
    private volatile int walMinFiles = 2; // Minimum 2 files
    private volatile int walPoolSize = 20; // upper limit for pre-created journal files
    private volatile int walCompactMinFiles = 10; // minimal number of files before we can considering compacting
    private volatile int walCompactPercentage = 30;
    private volatile String walFilePrefix = "slot-journal";
    private volatile String walFileExtension = "log";
    private volatile int walMaxIO = 2;
    private volatile boolean walSyncWrites = true;
    private volatile boolean walSyncDeletes = false;
    private volatile int walBufferFlushesPerSecond = 500;
    private volatile int walBufferSize = 490 * 1024;
    private volatile boolean walLogRates = false;
    private volatile boolean walAsyncIO = false;

    // Raft-specific properties (for JGroupsRaftSlots)
    private boolean raftEnabled = false;
    private boolean raftLogFsync = true;
    private String raftMembers = null;
    private int raftTimeout = 5000; // milliseconds
    private int raftElectionMinInterval = 150; // milliseconds
    private int raftElectionMaxInterval = 300; // milliseconds
    private int raftHeartbeatInterval = 50; // milliseconds

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
            synchronized (this) {
                if (cache == null) { // double-checked locking
                    cache = new ReplCache<>(jGroupsConfigFileName, getCacheName());
                    cache.setCallTimeout(1500L);
                    cache.setCachingTime(cachingTime);
                    cache.setMigrateData(true);
                }
            }
        }

        return cache;
    }

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
     * Get the L2 cache time in milliseconds.
     * The L2 cache is a local cache that reduces network calls by caching get() results.
     * Setting to 0 disables L2 caching for immediate consistency (recommended for WAL).
     * Setting to a positive value (e.g., 30000 for 30 seconds) improves performance but
     * may return stale data after remove() operations.
     *
     * @return caching time in milliseconds (0 = disabled, default)
     */
    public long getCachingTime() {
        return cachingTime;
    }

    public void setCachingTime(long cachingTime) {
        this.cachingTime = cachingTime;
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
            } else {
                super.setStoreDir(storeDir);
            }
        }
    }

    /**
     * TODO do we still need group names (they applied to the inifinispan store)
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
    public void setBackingSlots(BackingSlots backingSlots) {
        super.setBackingSlots(backingSlots);
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
                // double-checked locking
                if(jGroupsSlotKeyGenerator == null && (slotKeyGeneratorClassName != null && !slotKeyGeneratorClassName.isBlank())) {
                    jGroupsSlotKeyGenerator = ClassloadingUtility.loadAndInstantiateClass(JGroupsSlotKeyGenerator.class, slotKeyGeneratorClassName, null);
                }
            }
        }

        return jGroupsSlotKeyGenerator;
    }

    // ===== WAL (Write-Ahead Log) Properties =====

    /**
     * Enable Write-Ahead Log for JGroupsSlots persistence.
     * When enabled, all slot writes are logged to disk for crash recovery.
     *
     * @return true if WAL is enabled
     */
    public boolean isWalEnabled() {
        return walEnabled;
    }

    public void setWalEnabled(boolean walEnabled) {
        this.walEnabled = walEnabled;
    }

    /**
     * Enable fsync after each write to WAL.
     * When enabled, writes are durable (survive crash) but slower (~10-20ms).
     * When disabled, writes are faster (~1-2ms) but may be lost on crash.
     *
     * @return true if fsync is enabled for writes
     */
    public boolean isWalSyncWrites() {
        return walSyncWrites;
    }

    public void setWalSyncWrites(boolean walSyncWrites) {
        this.walSyncWrites = walSyncWrites;
    }

    /**
     * Enable fsync after each delete from WAL.
     * Usually not needed since deletes are less critical than writes.
     *
     * @return true if fsync is enabled for deletes
     */
    public boolean isWalSyncDeletes() {
        return walSyncDeletes;
    }

    public void setWalSyncDeletes(boolean walSyncDeletes) {
        this.walSyncDeletes = walSyncDeletes;
    }

    /**
     * Get the WAL buffer size in bytes for Artemis journal batching.
     * Larger buffers allow more writes to batch together before flushing.
     * Default: 490KB (matches HornetqJournalEnvironmentBean default)
     *
     * @return buffer size in bytes
     */
    public int getWalBufferSize() {
        return walBufferSize;
    }

    public void setWalBufferSize(int walBufferSize) {
        this.walBufferSize = walBufferSize;
    }

    /**
     * Get the WAL buffer flush rate (flushes per second).
     * Higher values = more frequent flushes = lower latency but less batching.
     * Lower values = less frequent flushes = higher latency but more batching.
     * Default: 300 (matches HornetqJournalEnvironmentBean default)
     *
     * @return flushes per second
     */
    public int getWalBufferFlushesPerSecond() {
        return walBufferFlushesPerSecond;
    }

    public void setWalBufferFlushesPerSecond(int walBufferFlushesPerSecond) {
        this.walBufferFlushesPerSecond = walBufferFlushesPerSecond;
    }

    /**
     * Get the desired size in bytes of each WAL journal file.
     * Default: 10MB (10485760 bytes)
     *
     * @return the individual log file size, in bytes
     */
    public int getWalFileSize() {
        return walFileSize;
    }

    public void setWalFileSize(int walFileSize) {
        this.walFileSize = walFileSize;
    }

    /**
     * Get the minimum number of WAL journal files to use.
     * Default: 2
     *
     * @return the minimum number of individual log files
     */
    public int getWalMinFiles() {
        return walMinFiles;
    }

    public void setWalMinFiles(int walMinFiles) {
        this.walMinFiles = walMinFiles;
    }

    /**
     * Get how many WAL journal files can be reused.
     * Default: 0 (no pooling)
     *
     * @return the number of files that can be reused
     */
    public int getWalPoolSize() {
        return walPoolSize;
    }

    public void setWalPoolSize(int walPoolSize) {
        this.walPoolSize = walPoolSize;
    }

    /**
     * Get the minimal number of files before WAL compaction can be considered.
     * Default: 0 (automatic compaction disabled)
     *
     * @return the threshold file count
     */
    public int getWalCompactMinFiles() {
        return walCompactMinFiles;
    }

    public void setWalCompactMinFiles(int walCompactMinFiles) {
        this.walCompactMinFiles = walCompactMinFiles;
    }

    /**
     * Get the percentage minimum capacity usage at which to start WAL compaction.
     * Default: 0 (compaction disabled)
     *
     * @return the threshold percentage
     */
    public int getWalCompactPercentage() {
        return walCompactPercentage;
    }

    public void setWalCompactPercentage(int walCompactPercentage) {
        this.walCompactPercentage = walCompactPercentage;
    }

    /**
     * Get the prefix to be used when naming each WAL journal file.
     * Default: "slot-journal"
     *
     * @return the prefix used to construct individual log file names
     */
    public String getWalFilePrefix() {
        return walFilePrefix;
    }

    public void setWalFilePrefix(String walFilePrefix) {
        this.walFilePrefix = walFilePrefix;
    }

    /**
     * Get the suffix to be used when naming each WAL journal file.
     * Default: "dat"
     *
     * @return the suffix used to construct individual log file names
     */
    public String getWalFileExtension() {
        return walFileExtension;
    }

    public void setWalFileExtension(String walFileExtension) {
        this.walFileExtension = walFileExtension;
    }

    /**
     * Get the maximum write requests queue depth for WAL.
     * For NIO this property has no effect and will be hard coded to 1.
     * For AIO, the default is 2 but the recommended value is 500.
     * Default: 1
     *
     * @return the max number of outstanding requests
     */
    public int getWalMaxIO() {
        return walMaxIO;
    }

    public void setWalMaxIO(int walMaxIO) {
        this.walMaxIO = walMaxIO;
    }

    /**
     * Get the IO type of WAL Journal.
     * Default: false (NIO)
     *
     * @return true if AsyncIO is enabled, false otherwise which means NIO
     */
    public boolean isWalAsyncIO() {
        return walAsyncIO;
    }

    /**
     * Set the type of WAL Journal.
     * Note that Journal silently falls back to NIO if AIO native libraries are not available.
     *
     * @param walAsyncIO true to enable AsyncIO, false to disable
     */
    public void setWalAsyncIO(boolean walAsyncIO) {
        this.walAsyncIO = walAsyncIO;
    }

    /**
     * Get the debug log mode for WAL Journal throughput statistics.
     * Default: false
     *
     * @return true if rate logging is enabled, false otherwise
     */
    public boolean isWalLogRates() {
        return walLogRates;
    }

    public void setWalLogRates(boolean walLogRates) {
        this.walLogRates = walLogRates;
    }

    // ===== Raft Properties =====
    /**
     * Enable JGroups-Raft consensus for strong consistency and persistent WAL.
     * When enabled, uses JGroupsRaftSlots instead of JGroupsSlots.
     *
     * @return true if Raft is enabled
     */
    public boolean isRaftEnabled() {
        return raftEnabled;
    }

    public void setRaftEnabled(boolean raftEnabled) {
        this.raftEnabled = raftEnabled;
    }

    /**
     * Enable fsync for Raft log writes. When true, all writes are forced to disk
     * before returning (provides crash recovery). When false, writes are buffered
     * (faster but not crash-safe).
     *
     * @return true if fsync is enabled
     */
    public boolean isRaftLogFsync() {
        return raftLogFsync;
    }

    public void setRaftLogFsync(boolean raftLogFsync) {
        this.raftLogFsync = raftLogFsync;
    }

    /**
     * Static membership list for Raft cluster (e.g., "NodeA,NodeB,NodeC").
     * Required when raftEnabled=true.
     *
     * @return comma-separated list of member names
     */
    public String getRaftMembers() {
        return raftMembers;
    }

    public void setRaftMembers(String raftMembers) {
        this.raftMembers = raftMembers;
    }

    /**
     * Timeout in milliseconds for Raft operations (default: 5000).
     *
     * @return timeout in milliseconds
     */
    public int getRaftTimeout() {
        return raftTimeout;
    }

    public void setRaftTimeout(int raftTimeout) {
        this.raftTimeout = raftTimeout;
    }

    /**
     * Minimum election timeout interval in milliseconds (default: 150).
     *
     * @return min election interval in milliseconds
     */
    public int getRaftElectionMinInterval() {
        return raftElectionMinInterval;
    }

    public void setRaftElectionMinInterval(int raftElectionMinInterval) {
        this.raftElectionMinInterval = raftElectionMinInterval;
    }

    /**
     * Maximum election timeout interval in milliseconds (default: 300).
     *
     * @return max election interval in milliseconds
     */
    public int getRaftElectionMaxInterval() {
        return raftElectionMaxInterval;
    }

    public void setRaftElectionMaxInterval(int raftElectionMaxInterval) {
        this.raftElectionMaxInterval = raftElectionMaxInterval;
    }

    /**
     * Heartbeat interval in milliseconds for Raft leader (default: 50).
     *
     * @return heartbeat interval in milliseconds
     */
    public int getRaftHeartbeatInterval() {
        return raftHeartbeatInterval;
    }

    public void setRaftHeartbeatInterval(int raftHeartbeatInterval) {
        this.raftHeartbeatInterval = raftHeartbeatInterval;
    }
}
