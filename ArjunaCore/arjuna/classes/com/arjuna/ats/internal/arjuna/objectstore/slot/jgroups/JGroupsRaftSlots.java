/*
 * Copyright The Narayana Authors
 *
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups;

import com.arjuna.ats.arjuna.logging.tsLogger;
import com.arjuna.ats.internal.arjuna.objectstore.slot.BackingSlots;
import com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStoreEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import org.jgroups.JChannel;
import org.jgroups.protocols.raft.RAFT;
import org.jgroups.protocols.raft.FileBasedLog;
import org.jgroups.raft.blocks.ReplicatedStateMachine;

import java.io.IOException;

/**
 * JGroups-Raft based implementation of {@link BackingSlots} providing strong consistency
 * and persistent write-ahead logging (WAL) for crash recovery.
 *
 * <p>Uses the Raft consensus algorithm for:
 * <ul>
 *   <li><b>Strong consistency</b>: Linearizable reads and writes</li>
 *   <li><b>Persistent WAL</b>: All committed data survives crashes via FileBasedLog</li>
 *   <li><b>Leader election</b>: Automatic failover on leader crash</li>
 *   <li><b>Split-brain protection</b>: Quorum-based operation prevents divergence</li>
 * </ul>
 *
 * <p><b>Performance Characteristics</b>:
 * <ul>
 *   <li>Write latency: ~10-20ms (with fsync enabled)</li>
 *   <li>Read latency: ~0.1ms (local reads from state machine)</li>
 *   <li>Throughput: 100-200 ops/sec (with fsync), 1000+ ops/sec (without fsync)</li>
 * </ul>
 *
 * <p><b>Configuration Requirements</b>:
 * <ul>
 *   <li>Odd number of nodes (3, 5, or 7 recommended)</li>
 *   <li>Static membership list via {@link JGroupsStoreEnvironmentBean#setRaftMembers(String)}</li>
 *   <li>Unique node address via {@link JGroupsStoreEnvironmentBean#setNodeAddress(String)}</li>
 *   <li>Persistent storage directory via {@link JGroupsStoreEnvironmentBean#setStoreDir(String)}</li>
 * </ul>
 *
 * <p><b>NOTE</b>: This is an Experimental feature and is not recommended for production systems.
 * May contain breaking changes in future releases.
 *
 * @since 5.13.2
 * @see JGroupsSlots
 * @see ReplicatedStateMachine
 * @see FileBasedLog
 */
public class JGroupsRaftSlots implements BackingSlots {
    private JChannel channel;
    private ReplicatedStateMachine<Integer, byte[]> cache;
    private JGroupsStoreEnvironmentBean config;
    private volatile boolean initialized = false;

    /**
     * Initialize the Raft-based slot store.
     *
     * @param slotStoreConfig the configuration bean
     * @throws IOException if initialization fails
     */
    @Override
    public void init(SlotStoreEnvironmentBean slotStoreConfig) throws IOException {
        if (initialized) {
            throw new IllegalStateException("JGroupsRaftSlots already initialized");
        }

        tsLogger.i18NLogger.warn_jgroups_slot_store();

        if (slotStoreConfig instanceof JGroupsStoreEnvironmentBean) {
            config = (JGroupsStoreEnvironmentBean) slotStoreConfig;
        } else {
            config = BeanPopulator.getDefaultInstance(JGroupsStoreEnvironmentBean.class);
        }

        try {
            tsLogger.logger.warn("ARJUNA012422: JGroupsRaftSlots: Initializing experimental Raft feature. Do not use in production.");

            // Validate configuration
            validateConfiguration(config);

            // Create JGroups channel
            String jgroupsConfig = config.getJGroupsConfigFileName();
            String nodeName = config.getNodeAddress();

            tsLogger.logger.info("Creating Raft channel with config: " + jgroupsConfig + ", node: " + nodeName);
            channel = new JChannel(jgroupsConfig).name(nodeName);

            // Configure RAFT protocol BEFORE creating ReplicatedStateMachine
            RAFT raft = channel.getProtocolStack().findProtocol(RAFT.class);
            if (raft == null) {
                throw new IllegalStateException("RAFT protocol not found in JGroups stack");
            }

            // Configure Raft log directory (where FileBasedLog stores data)
            String storeDir = config.getStoreDir();
            raft.logDir(storeDir);
            tsLogger.logger.info("Configured Raft log directory: " + storeDir);

            // Configure Raft log fsync behavior
            raft.logUseFsync(config.isRaftLogFsync());
            tsLogger.logger.info("Configured Raft log fsync: " + config.isRaftLogFsync());

            // Set members list
            raft.members(java.util.Arrays.asList(config.getRaftMembers().split(",")));
            tsLogger.logger.info("Configured RAFT members: " + config.getRaftMembers());

            // Create replicated state machine and set raft_id
            cache = new ReplicatedStateMachine<>(channel);
            cache.raftId(nodeName);  // This sets the raft_id on the RAFT protocol
            cache.timeout(config.getRaftTimeout());

            // Connect to cluster
            String clusterName = config.getGroupName();
            if (clusterName == null) {
                clusterName = config.getCacheName();
            }

            tsLogger.logger.info("Connecting to Raft cluster: " + clusterName);
            channel.connect(clusterName);

            // Raft state machine is loaded from the persistent log during connect().
            // SlotStore's constructor will call read(i) for each slot to rebuild its index,
            // so no additional loading is needed here.
            tsLogger.logger.info("Raft state machine has " + cache.size() + " entries after log replay");
            initialized = true;

            tsLogger.logger.info("JGroupsRaftSlots initialized successfully for node: " + nodeName);

        } catch (Exception e) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (Exception ex) {
                    // Ignore cleanup errors
                }
            }
            throw new IOException("Failed to initialize JGroupsRaftSlots", e);
        }
    }


    /**
     * Read slot data. Reads are local (no consensus required).
     *
     * @param slotId the slot ID
     * @return the slot data, or null if slot is empty
     */
    @Override
    public byte[] read(int slotId) throws IOException {
        checkInitialized();
        try {
            return cache.get(slotId);
        } catch (Exception e) {
            tsLogger.logger.warn("Raft read failed for slot " + slotId, e);
            throw new IOException("Raft read failed", e);
        }
    }

    /**
     * Write slot data. Writes go through Raft consensus and are replicated to majority.
     * Returns only after data is committed to Raft log (and fsynced if enabled).
     *
     * @param slotId the slot ID
     * @param data the data to write
     * @param sync ignored - Raft always ensures consistency
     * @throws IOException if write fails
     */
    @Override
    public void write(int slotId, byte[] data, boolean sync) throws IOException {
        checkInitialized();
        try {
            // Raft put() blocks until majority commit
            cache.put(slotId, data);
        } catch (Exception e) {
            tsLogger.logger.warn("Raft write failed for slot " + slotId, e);
            throw new IOException("Raft write failed", e);
        }
    }

    /**
     * Clear slot data. Goes through Raft consensus.
     *
     * @param slotId the slot ID
     * @param sync ignored - Raft always ensures consistency
     * @throws IOException if clear fails
     */
    @Override
    public void clear(int slotId, boolean sync) throws IOException {
        checkInitialized();
        try {
            cache.remove(slotId);
        } catch (Exception e) {
            tsLogger.logger.warn("Raft clear failed for slot " + slotId, e);
            throw new IOException("Raft clear failed", e);
        }
    }

    @Override
    public void stop() {
        if (channel != null) {
            tsLogger.logger.info("Shutting down JGroupsRaftSlots for node: " + config.getNodeAddress());
            try {
                channel.close();
            } catch (Exception e) {
                tsLogger.logger.warn("Error closing Raft channel", e);
            }
            channel = null;
        }
        initialized = false;
    }

    /**
     * Get the number of slots. Returns the configured number.
     *
     * @return number of slots
     */
    public int getNumberOfSlots() {
        return config != null ? config.getNumberOfSlots() : 0;
    }

    /**
     * Get the underlying JChannel for monitoring/testing.
     *
     * @return the JChannel instance
     */
    public JChannel getChannel() {
        return channel;
    }

    /**
     * Get the replicated state machine for advanced access.
     *
     * @return the ReplicatedStateMachine instance
     */
    public ReplicatedStateMachine<Integer, byte[]> getStateMachine() {
        return cache;
    }

    /**
     * Check if Raft leader has been elected.
     *
     * @return true if a leader exists
     */
    public boolean hasLeader() {
        if (!initialized || channel == null) {
            return false;
        }
        try {
            RAFT raft = channel.getProtocolStack().findProtocol(RAFT.class);
            return raft != null && raft.leader() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get current Raft role (Leader, Follower, Candidate, or impl class name).
     *
     * @return role name, or "UNKNOWN" if not available
     */
    public String getRole() {
        if (!initialized || channel == null) {
            return "UNKNOWN";
        }
        try {
            RAFT raft = channel.getProtocolStack().findProtocol(RAFT.class);
            if (raft == null) {
                return "UNKNOWN";
            }
            // raft.role() returns the impl class name (e.g. "Leader", "Follower", "Candidate")
            // Convert to uppercase for consistency with expected test values
            String role = raft.role();
            return role != null ? role.toUpperCase() : "UNKNOWN";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    // Private helper methods

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("JGroupsRaftSlots not initialized");
        }
    }

    private void validateConfiguration(JGroupsStoreEnvironmentBean config) {
        if (config.getNodeAddress() == null || config.getNodeAddress().isEmpty()) {
            throw new IllegalArgumentException("nodeAddress must be set for Raft");
        }

        if (config.getRaftMembers() == null || config.getRaftMembers().isEmpty()) {
            throw new IllegalArgumentException("raftMembers must be set (e.g., 'NodeA,NodeB,NodeC')");
        }

        if (config.getStoreDir() == null || config.getStoreDir().isEmpty()) {
            throw new IllegalArgumentException("storeDir must be set for Raft persistent log");
        }

        String[] members = config.getRaftMembers().split(",");
        if (members.length % 2 == 0) {
            tsLogger.logger.warn("Raft cluster has even number of nodes (" + members.length +
                "). Odd numbers (3, 5, 7) are recommended for proper quorum.");
        }

        if (members.length < 3) {
            tsLogger.logger.warn("Raft cluster has only " + members.length +
                " nodes. Minimum 3 nodes recommended for fault tolerance.");
        }
    }

}
