package com.hp.mwtests.ts.arjuna.recovery;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.RecoveryEnvironmentBean;
import com.arjuna.ats.arjuna.common.recoveryPropertyManager;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeManager;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeMap;
import com.arjuna.ats.arjuna.recovery.RecoveryManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.arjuna.ats.arjuna.coordinator.RecordType.USER_DEF_LAST0;

public class RecoveryShutdownTest {
    private CrashRecord cr1, cr2;
    private static AtomicInteger commitCount = new AtomicInteger(0);
    private static RecoveryManager manager;

    @BeforeAll
    public static void beforeClass() {
        // register the CrashRecord type with the recovery system
        RecordTypeManager.manager().add(new TestRecordMap());
        List<String> types = new ArrayList<>();
        RecoveryEnvironmentBean recoveryConfig = recoveryPropertyManager.getRecoveryEnvironmentBean();
        String[] modules = {
                // the modules to test
                "com.arjuna.ats.internal.arjuna.recovery.AtomicActionRecoveryModule",
//                "com.arjuna.ats.internal.jta.recovery.arjunacore.CommitMarkableResourceRecordRecoveryModule",
//                "com.arjuna.ats.internal.txoj.recovery.TORecoveryModule",
//                "com.arjuna.ats.internal.jts.recovery.transactions.TopLevelTransactionRecoveryModule",
//                "com.arjuna.ats.internal.jts.recovery.transactions.ServerTransactionRecoveryModule",
//                "com.arjuna.ats.internal.jta.recovery.arjunacore.SubordinateAtomicActionRecoveryModule",
//                "com.arjuna.ats.internal.jta.recovery.jts.XARecoveryModule"
        };

        types.add(new AtomicAction().type()); // AtomicActionRecoveryModule

        recoveryConfig.setRecoveryBackoffPeriod(1); // in between passes
        recoveryConfig.setPeriodicRecoveryPeriod(20); // 20 seconds

        recoveryConfig.setRecoveryModuleClassNames(Arrays.asList(modules)); // the test set of modules
        recoveryConfig.setTypeNamesToBlockShutdown(types); // tell the system which record types to stall shutdown for
        recoveryConfig.setWaitForFinalRecovery(true); // don't sign off until the store is empty

        // obtain a new RecoveryManager with the above config:
        manager = RecoveryManager.manager(RecoveryManager.INDIRECT_MANAGEMENT);
    }

    @Test
    public void testShutdown() {
        // create a recovery record corresponding to a type that stalls shutdown
        createRecoveryRecord();

        // start recovery
        manager.startRecoveryManagerThread();

        // join this thread with the recovery manager thread, so it should not return until
        // replayPhase2 finishes successfully
        manager.terminate();

        // commit should have been called a second time during recovery
        Assertions.assertEquals(4, commitCount.get(), "Wrong number of commit attempts during replay");
    }

    private void createRecoveryRecord() {
        AtomicAction A = new AtomicAction();

        cr1 = new CrashRecord(USER_DEF_LAST0, CrashAbstractRecordImpl.CRASH_IN_COMMIT);
        cr2 = new CrashRecord(USER_DEF_LAST0, CrashAbstractRecordImpl.CRASH_IN_COMMIT);

        A.begin();

        A.add(cr1); // will cause a crash in commit
        A.add(cr2); // will cause a crash in commit

        A.commit(); // should generate a recovery record

        Assertions.assertEquals(2, commitCount.get(), "Wrong number of commit attempts during commit");
    }

    // register the type CrashRecord against USER_DEF_LAST0
    static class TestRecordMap implements RecordTypeMap {
        public Class<CrashRecord> getRecordClass () {
            return CrashRecord.class;
        }

        public int getType () {
            return USER_DEF_LAST0;
        }
    }

    // needs to be public since it gets created dynamically during recovery
    public static class CrashRecord extends CrashAbstractRecordImpl {
        int recordType;

        // no-args constructor is called during recovery
        public CrashRecord() {
            this(USER_DEF_LAST0, NO_CRASH); // NO_CRASH since we want recovery to succeed
        }

        public CrashRecord(int recordType, int crashBehaviour) {
            super(crashBehaviour);

            this.recordType = recordType;
        }

        @Override
        public int typeIs()
        {
            return recordType;
        }

        @Override
        public int topLevelCommit() {
            commitCount.incrementAndGet();

            return super.topLevelCommit();
        }
    }
}
