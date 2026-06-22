/*
   Copyright The Narayana Authors
   SPDX-License-Identifier: Apache-2.0
 */
package com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.logging.tsLogger;
import com.arjuna.ats.internal.arjuna.objectstore.slot.BackingSlots;
import com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStoreEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import org.jgroups.blocks.ReplCache;

import java.io.IOException;
import java.util.Set;

/**
 * A {@link com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStore} implementation backed by an jGroups cache.
 * It is an in-memory datastore and can be backed by a cluster of jGroups nodes to maintain data
 * availability provided the caches are suitably configured to manage replication of data across the cluster.
 * If the store is to be used with a recovery manager it is important that the environment is configured such that the
 * usual caveats are maintained:
 * - jgroups2-raft, or equivalent, is used to ensure strict consistency as required by a CP system
 * - only one recovery manager acts as the leader
 * - if the leader fails automatic failover chooses a new leader while avoiding split brain scenarios
 * - etc.
 * Maintaining these requirements is non-trivial and requires extra support from the environment.
 * <p>
 * The interface is internal and is used by the {@link com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStoreAdaptor}
 * and should not be called independently of the transaction and recovery systems.
 */
/*
 * Implementation notes:
 * A record is written to the store using
 * SlotStoreAdaptor#write_committed(Uid uid, String typeName, OutputObjectState outputObjectState).
 * From this data a SlotStoreKey key is created (new SlotStoreKey(uid, typeName, StateStatus.OS_COMMITTED);) and the Uid
 * and the type of the record serve as a key for the SlotStore and the data is the object state (outputObjectState).
 * The key is then packed into the object state (so now the outputObjectState contains both the key and the object store
 * record). The key is used as a key into SlotStore ConcurrentHashMap<SlotStoreKey, Integer> slotIdIndex and this
 * slotIdIndex "tracks the key to slot mapping for all in-use slots".
 *
 * The new data is placed in a free slot (Integer slotId = freeList.poll(); and slotIdIndex.put(key, slotId);), and the
 * data is then written to the actual backing slots implementation using slots.write(slotId, data).
 * The backend manages its own mapping of the slotId to the data. In the JGroupsSlots backend the data is in byte[][]
 * slots and is an array of keys (of type byte[]). When the backend initialises, it gets the current cache.keySet() and
 * places each cache entry key into one of the slots and initialises the remaining slots with a unique key (it uses an
 * instance of JGroupsSlotKeyGenerator defined in the JGroupsStoreEnvironmentBean config to get the unique key but
 * the generator can be anything that produces a unique entry for the slots table - it has to be unique because it is
 * used for the cache entry keys which are distributed to other nodes in cluster setups so minimally the Uid class
 * would be sufficient, in fact this is the default if no key generator is defined.
 *
 * With replicated or distributed caches, writes to the cache update other cluster nodes and when another node reads
 * the entry it uses the key to populate an entry in its own slot table.
 *
 * So with all that, now it's possible to go from a Uid and typeName (which all arjuna records contain)
 * to the SlotStoreKey to the slot index (via slotIdIndex) to the actual data returned from the backing slots
 * read(byte[] read(int slot) method which does the actual jGroups cache lookup to get the data).
 */
public class JGroupsSlots implements BackingSlots {
    private ByteArrayKey[] slots = null;
    private ReplCache<ByteArrayKey, byte[]> cache;
    private JGroupsSlotKeyGenerator jGroupsSlotKeyGenerator;
    private short replicationCount = -1;
    private SlotJournal journal = null;  // Optional WAL for persistence

    /**
     * Overrides {@link BackingSlots#init(SlotStoreEnvironmentBean)} and has the same meaning
     * @param slotStoreConfig the config to use for the initialisation
     * @throws IOException if the cache operation threw an exception
     */
    @Override
    public void init(SlotStoreEnvironmentBean slotStoreConfig) throws IOException {
        JGroupsStoreEnvironmentBean config;

        tsLogger.i18NLogger.warn_jgroups_slot_store();

        if (slotStoreConfig instanceof JGroupsStoreEnvironmentBean) {
            config = (JGroupsStoreEnvironmentBean) slotStoreConfig;
        } else {
            config = BeanPopulator.getDefaultInstance(JGroupsStoreEnvironmentBean.class);
        }

        slots = new ByteArrayKey[slotStoreConfig.getNumberOfSlots()];
        jGroupsSlotKeyGenerator = config.getSlotKeyGenerator();

        if (jGroupsSlotKeyGenerator == null) {
            jGroupsSlotKeyGenerator = new JGroupsSlotKeyGenerator() {
                @Override
                public ByteArrayKey generateUniqueKey(int index) {
                    return new ByteArrayKey(new Uid().getBytes());
                }

                @Override
                public void init(JGroupsStoreEnvironmentBean ignore) {
                }
            };
        }
        jGroupsSlotKeyGenerator.init(config);

        try {
            // Initialize WAL if enabled
            if (config.isWalEnabled()) {
                String storeDir = config.getStoreDir();
                if (storeDir == null || storeDir.isEmpty()) {
                    throw new IllegalArgumentException("storeDir must be set when WAL is enabled");
                }

                tsLogger.logger.info("JGroupsSlots: Enabling WAL with storeDir=" + storeDir +
                    ", syncWrites=" + config.isWalSyncWrites() +
                    ", syncDeletes=" + config.isWalSyncDeletes());

                journal = new SlotJournal(storeDir, config.isWalSyncWrites(), config.isWalSyncDeletes());
                journal.start();

                tsLogger.logger.info("JGroupsSlots: WAL loaded " + journal.size() + " slots from disk");
            }

            // set up the slot keys
            String group = config.getGroupName();

            cache = config.getCache();
            replicationCount = config.getReplicationCount();
            cache.start();

            // Load slots from cache or WAL
            if (journal != null) {
                // WAL enabled: load from journal first, then merge with cache
                loadFromWAL();
            }

//            if (group != null && !group.isEmpty())
//                load(cache.getAdvancedCache().getGroup(group).keySet());
//            else
//                load(cache.getL2Cache().getInternalMap().keySet()); // TODO check that these are the correct keys
            load(cache.getL2Cache().getInternalMap().keySet());
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Load slots from WAL into cache.
     * Called during initialization if WAL is enabled.
     */
    private void loadFromWAL() throws Exception {
        if (journal == null) {
            return;
        }

        int recoveredCount = 0;
        for (Integer slotId : journal.getSlotIds()) {
            if (slotId >= 0 && slotId < slots.length) {
                byte[] data = journal.read(slotId);
                if (data != null) {
                    // Restore to cache
                    cache.put(slots[slotId], data, replicationCount, 0);
                    recoveredCount++;
                }
            }
        }

        tsLogger.logger.info("JGroupsSlots: Recovered " + recoveredCount + " slots from WAL to cache");
    }

    /**
     * Overrides {@link BackingSlots#write(int, byte[], boolean)}
     * The write semantics depend on how the cache was configured {@link JGroupsStoreEnvironmentBean#setCache(ReplCache)}
     *
     * Overrides @link {BackingSlots} and has the same meaning
     *
     * @param slot the index, from 0 to config numberOfSlots-1
     * @param data the content.
     * @param sync not used (use {@link JGroupsStoreEnvironmentBean#setReplicationCount} to control how write operations
     *             behave)
     *
     * @throws IOException if the cache operation threw an exception
     */
    @Override
    public void write(int slot, byte[] data, boolean sync) throws IOException {
        try {
            // Write to WAL first (if enabled) for durability
            if (journal != null) {
                journal.write(slot, data);
            }

            /*
             * cache the value until explicitly removed (timeout 0) by the transaction manager.
             * The replicationCount controls how many nodes will see the write operation,
             * -1 means don't cache at all in the L1 cache (L1 is the local cache L2 is the distributed one).
             *
             * A non-zero timeout value is the number of milliseconds to keep an idle (unaccessed) element in the cache
             * - we never want to timeout entries instead relying on the TM to explicitly remove the item when it
             * is no longer in doubt.
             */
            cache.put(slots[slot], data, replicationCount, 0);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Overrides {@link BackingSlots#read(int)}
     * The read semantics depend on how the cache ({@link JGroupsStoreEnvironmentBean#setCache(ReplCache)} setCache(Cache)})
     * was configured
     *
     * @param slot the index, from 0 to config numberOfSlots-1
     *
     * @throws IOException if the cache operation threw an exception
     */
    @Override
    public byte[] read(int slot) throws IOException {
        try {
            byte[] data = cache.get(slots[slot]);

            // If not in cache but WAL enabled, try WAL (shouldn't happen normally)
            if (data == null && journal != null) {
                data = journal.read(slot);
            }

            return data;
        } catch (Exception e) {
            // TODO figure out why InfinispanSlots doesn't hit this problem -
            // I suspect there something amiss with the JGroups cluster config
            return null;
//            throw new IOException(e);
        }
    }

    /**
     * Overrides {@link BackingSlots#clear(int, boolean)} and has the same meaning
     * @param slot the index, from 0 to config numberOfSlots-1
     * @param sync not used because the sync behaviour depends on the cache configuration
     * @throws IOException if the cache operation threw an exception
     */
    @Override
    public void clear(int slot, boolean sync) throws IOException {
        try {
            // Delete from WAL first (if enabled)
            if (journal != null) {
                journal.delete(slot);
            }

            // remove an entry from the entire cache system (it's important to use this method instead of evict)
            cache.remove(slots[slot]);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Shutdown the store, closing the WAL if enabled.
     */
    public void shutdown() {
        if (journal != null) {
            try {
                journal.stop();
                tsLogger.logger.info("JGroupsSlots: WAL stopped");
            } catch (Exception e) {
                tsLogger.logger.warn("JGroupsSlots: Error stopping WAL: " + e.getMessage());
            }
        }
    }

    private void load(Set<ByteArrayKey> keys) throws IOException {
        int i = 0;

        for (ByteArrayKey key : keys) {
            if (i < slots.length) {
                slots[i] = key;
                i += 1;
            } else {
                /*
                 * The number of slots should equal the maximum number of unresolved transactions expected at any given
                 * time, including those in-flight and awaiting recovery.
                 */
                String errorMsg = tsLogger.i18NLogger.get_jgroups_too_few_slots(keys.size(), slots.length);

                throw new IOException(errorMsg);
            }
        }

        // initialise the remaining slots
        while (i < slots.length) {
            try {
                slots[i] = jGroupsSlotKeyGenerator.generateUniqueKey(i);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            i += 1;
        }
    }
}
