package com.hp.mwtests.ts.arjuna.atomicaction;

import com.arjuna.ats.arjuna.AtomicAction;

import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.arjuna.coordinator.RecordType;

import static org.junit.Assert.assertTrue;

import com.arjuna.ats.arjuna.coordinator.TwoPhaseOutcome;
import org.junit.Test;
import org.junit.Assert;
import org.junit.BeforeClass;


public class VirtualThreadTest {
    private static boolean isAsync = true;

    @BeforeClass
    public static void beforeClass() throws Exception {
        arjPropertyManager.getCoordinatorEnvironmentBean().setAsyncPrepare(isAsync);
        arjPropertyManager.getCoordinatorEnvironmentBean().setAsyncCommit(isAsync);
        arjPropertyManager.getCoordinatorEnvironmentBean().setAsyncRollback(isAsync);

        arjPropertyManager.getCoordinatorEnvironmentBean().setAsyncBeforeSynchronization(isAsync);
        arjPropertyManager.getCoordinatorEnvironmentBean().setAsyncAfterSynchronization(isAsync);
    }

    @Test
    public void testCanCommitSuspendedTransaction() throws Exception
    {
        AtomicAction aa = new AtomicAction();
        aa.begin();
        assertTrue(AtomicAction.Current() != null);
        AtomicAction.suspend();
        assertTrue(AtomicAction.Current() == null);
        SimpleAbstractRecord ar1 = new SimpleAbstractRecord();
        SimpleAbstractRecord ar2 = new SimpleAbstractRecord();
        SimpleAbstractRecord ar3 = new SimpleAbstractRecord();
        aa.add(ar1);
        aa.add(ar2);
        aa.add(ar3);
        aa.commit();
        assertTrue(ar1.wasCommitted());
    }

    private class SimpleAbstractRecord extends AbstractRecord {
		private boolean wasCommitted;

        @Override
        public int typeIs() {
            return RecordType.USER_DEF_FIRST0;
        }

		public boolean wasCommitted() {
            System.out.printf("SimpleAbstractRecord committed on thread %s\n", Thread.currentThread().getName());
			return wasCommitted;
		}

        @Override
        public Object value() {
            return null;
        }

        @Override
        public void setValue(Object o) {

        }

        @Override
        public int nestedAbort() {
            return 0;
        }

        @Override
        public int nestedCommit() {
            return 0;
        }

        @Override
        public int nestedPrepare() {
            return 0;
        }

        @Override
        public int topLevelAbort() {
            return 0;
        }

        @Override
        public int topLevelCommit() {
            wasCommitted = true;
            return TwoPhaseOutcome.FINISH_OK;
        }

        @Override
        public int topLevelPrepare() {
            return TwoPhaseOutcome.PREPARE_OK;
        }

        @Override
        public void merge(AbstractRecord a) {

        }

        @Override
        public void alter(AbstractRecord a) {

        }

        @Override
        public boolean shouldAdd(AbstractRecord a) {
            return true; // force it onto the pending list
        }

        @Override
        public boolean shouldAlter(AbstractRecord a) {
            return false;
        }

        @Override
        public boolean shouldMerge(AbstractRecord a) {
            return false;
        }

        @Override
        public boolean shouldReplace(AbstractRecord a) {
            return false;
        }
    }
}
