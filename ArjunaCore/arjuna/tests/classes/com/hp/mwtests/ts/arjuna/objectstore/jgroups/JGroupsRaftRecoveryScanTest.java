/*
   Copyright The Narayana Authors
   SPDX-License-Identifier: Apache-2.0
 */

package com.hp.mwtests.ts.arjuna.objectstore.jgroups;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.RecoveryEnvironmentBean;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.common.recoveryPropertyManager;
import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.arjuna.coordinator.ActionStatus;
import com.arjuna.ats.arjuna.coordinator.RecordType;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeManager;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeMap;
import com.arjuna.ats.arjuna.exceptions.ObjectStoreException;
import com.arjuna.ats.arjuna.objectstore.RecoveryStore;
import com.arjuna.ats.arjuna.recovery.RecoveryManager;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsRaftSlots;
import com.arjuna.ats.internal.arjuna.objectstore.slot.jgroups.JGroupsStoreEnvironmentBean;
import com.arjuna.ats.internal.arjuna.recovery.AtomicActionRecoveryModule;
import com.hp.mwtests.ts.arjuna.resources.CrashRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests showing that periodic recovery can recover in doubt actions in the JGroups Raft slot store.
 * This test is analogous to InfinispanRecoveryScanTest and JGroupsRecoveryScanTest but uses
 * JGroupsRaftSlots which provides strong consistency via the Raft consensus protocol.
 */
public class JGroupsRaftRecoveryScanTest extends JGroupsTestBase {

    static RecoveryManager manager;
    static RaftStore store;
    static RecoveryStore recoveryStore;
    private static final String RAFT_STORE_DIR = System.getProperty("user.dir") + "/target/jgroups-raft-recovery-test";

    /**
     * Wrapper for Raft store configuration and lifecycle.
     */
    static class RaftStore {
        final JGroupsStoreEnvironmentBean config;
        final String nodeName;

        RaftStore(String nodeName, String groupName) throws Exception {
            this.nodeName = nodeName;
            this.config = new JGroupsStoreEnvironmentBean();

            // Basic configuration
            config.setJGroupsConfigFileName("jgroups-raft.xml");
            config.setNodeAddress(nodeName);
            config.setGroupName(groupName);
            config.setCacheName(groupName);
            config.setStoreDir(RAFT_STORE_DIR + "/" + nodeName);
            config.setNumberOfSlots(256);
            config.setSlotKeyGeneratorClassName(SharedSlotKeyGenerator.class.getName());

            // Raft-specific configuration
            config.setRaftEnabled(true);
            config.setRaftMembers(nodeName);  // Single node for recovery test
            config.setRaftLogFsync(false);    // Disable fsync for faster tests
            config.setRaftTimeout(2000);

            // Create and set the backing slots
            JGroupsRaftSlots slots = new JGroupsRaftSlots();
            config.setBackingSlots(slots);
        }

        RecoveryStore start() throws Exception {
            // startRecoveryStore will initialize the slots and wait for leader election
            // (leader election wait is now built into JGroupsRaftSlots.init())
            RecoveryStore recoveryStore = startRecoveryStore(config);

            System.out.println("RaftStore initialized for node: " + nodeName);
            return recoveryStore;
        }

        void stop() {
            // StoreManager will handle cleanup
        }
    }

    @BeforeAll
    public static void beforeAll() throws Exception {
        // Clean up any previous test data
        cleanupStoreDir();

        // Register the record type for recovery
        RecordTypeManager.manager().add(new RecoverableCrashRecordTypeMap());

        // Create and start the JGroups Raft store (initializes slots and waits for leader)
        store = new RaftStore("raft-node1", CLUSTER_NAME);
        recoveryStore = store.start(); // Returns the recovery store

        // Setup recovery system
        RecoveryEnvironmentBean recoveryEnvironmentBean = recoveryPropertyManager.getRecoveryEnvironmentBean();
        recoveryEnvironmentBean.setRecoveryBackoffPeriod(1); // use a short interval between passes
        recoveryEnvironmentBean.setRecoveryListener(true); // configure the RecoveryMonitor

        manager = RecoveryManager.manager(RecoveryManager.DIRECT_MANAGEMENT);

        try {
            resetAtomicActionRecoveryModule();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        AtomicActionRecoveryModule aaRecoveryModule = new AtomicActionRecoveryModule();
        manager.addModule(aaRecoveryModule);
    }

    @AfterAll
    static void afterAll() {
        if (manager != null) {
            manager.terminate();
            manager = null;
        }
        if (recoveryStore != null) {
            recoveryStore.stop();
            recoveryStore = null;
        }
        if (store != null) {
            store.stop();
            store = null;
        }

        // skip raft log clean up
        //   The FileBasedLog (JGroups Raft's persistent log implementation) stores:
        //  - Raft log entries: All committed state machine operations
        //  - Metadata: Current term, voted-for, commit index
        //  - Snapshots: Periodic compaction of the log
        //cleanupStoreDir();
    }

    @Test
    public void testRecovery() throws Exception {
        Uid uid = new Uid();
        AtomicAction aa = new AtomicAction(uid);

        aa.begin();

        aa.add(new RecoverableCrashRecord(CrashRecord.CrashLocation.NoCrash, CrashRecord.CrashType.Normal));
        aa.add(new RecoverableCrashRecord(CrashRecord.CrashLocation.CrashInCommit, CrashRecord.CrashType.Normal));

        Assertions.assertEquals(ActionStatus.COMMITTED, aa.commit(true)); // normal crash in commit

        // Verify that there is something in the store (the in-doubt transaction)
        try {
            recoveryStore.read_committed(aa.getSavingUid(), aa.type());
        } catch (ObjectStoreException e) {
            fail("Record should be available in the recovery store: " + e);
        }

        /*
         * The recovery scan should find the atomic action with one prepared record which will be recovered via the
         * AtomicActionRecoveryModule.doRecoverTransaction logic which recreates the second RecoverableCrashRecord
         * using the empty constructor (via AbstractRecord.create) which does not set any CrashRecord.CrashLocation
         * so the commit will be replayed successfully.
         */
        manager.startRecoveryManagerThread(); // start periodic recovery
        manager.scan();

        // Wait for recovery to complete (give it some time to process)
        waitFor(REPLICATION_TIMEOUT_MS, "recovery to complete", () -> {
            try {
                recoveryStore.read_committed(aa.getSavingUid(), aa.type());
                return false; // Still there, not recovered yet
            } catch (ObjectStoreException e) {
                return true; // Gone, successfully recovered
            }
        });

        // Verify that the atomic action was recovered
        try {
            recoveryStore.read_committed(aa.getSavingUid(), aa.type());
            fail("The recovery scan should have committed the action");
        } catch (ObjectStoreException ignore) {
            // Expected - the record should be gone after recovery
        }
    }

    private static void cleanupStoreDir() {
        try {
            Path storePath = Paths.get(RAFT_STORE_DIR);
            if (Files.exists(storePath)) {
                Files.walk(storePath)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                // Ignore
                            }
                        });
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    /**
     * RecoverableCrashRecord that can be reconstructed during recovery.
     */
    public static class RecoverableCrashRecord extends CrashRecord {

        // Default constructor needed for recovery via AbstractRecord.create
        public RecoverableCrashRecord() {
            super();
        }

        public RecoverableCrashRecord(CrashLocation crashLocation, CrashType crashType) {
            super(crashLocation, crashType);
        }

        @Override
        public boolean shouldAdd(AbstractRecord a) {
            return true;
        }
    }

    /**
     * RecordTypeMap is the Arjuna mechanism that facilitates {@link AbstractRecord#create(int)}
     * to create specific record types during recovery.
     */
    static class RecoverableCrashRecordTypeMap implements RecordTypeMap {
        public Class<RecoverableCrashRecord> getRecordClass() {
            return RecoverableCrashRecord.class;
        }

        public int getType() {
            // The type must be registered in the RecordType registry
            int recordType = new RecoverableCrashRecord().typeIs();
            Assertions.assertEquals(RecordType.USER_DEF_FIRST0, recordType);
            return recordType;
        }
    }
}
