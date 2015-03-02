package com.hp.mwtests.ts.arjuna.tools.newosb;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.arjuna.coordinator.ActionStatus;
import com.arjuna.ats.arjuna.coordinator.RecordType;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeManager;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeMap;
import com.arjuna.ats.arjuna.tools.osb.mbean.OSEntryBeanMXBean;
import com.arjuna.ats.internal.arjuna.tools.osb.ObjStoreBrowser;
import com.hp.mwtests.ts.arjuna.resources.CrashRecord;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;

public class OSBTest {
    //@Test
    public void test() {
//        StoreManager.getTxLog().write_committed(new Uid(), "foo", new OutputObjectState());
//        StoreManager.getTxLog().write_committed(new Uid(), "foo", new OutputObjectState());

        ObjStoreBrowser osb = new ObjStoreBrowser();
        Collection<OSEntryBeanMXBean> mbeans = new ArrayList<>();

        osb.probe();

        osb.getMBeans(mbeans);

        System.out.printf("found %d mbeans%n", mbeans.size());
    }

    @Test
    public void test2() throws Exception {
        aaTest(false);
    }

	public void aaTest(boolean replay) throws Exception {
        ObjStoreBrowser osb = new ObjStoreBrowser();
		AtomicAction A = new AtomicAction();
		CrashRecord recs[] = {
				new CrashRecord(CrashRecord.CrashLocation.NoCrash, CrashRecord.CrashType.Normal),
				new CrashRecord(CrashRecord.CrashLocation.CrashInCommit, CrashRecord.CrashType.HeuristicHazard)
		};

		// register CrashRecord record type so that it is persisted in the object store correctly
		RecordTypeManager.manager().add(new RecordTypeMap() {
			public Class<? extends AbstractRecord> getRecordClass () { return CrashRecord.class;}
			public int getType () {return RecordType.USER_DEF_FIRST0;}
		});

		// create an atomic action, register crash records with it and then commit
		A.begin();

		for (CrashRecord rec : recs)
			A.add(rec);

		int outcome = A.commit();

		// the second participant should have generated a heuristic during commit
		assertEquals(ActionStatus.H_HAZARD, outcome);

        Collection<OSEntryBeanMXBean> mbeans = new ArrayList<>();

        osb.start();
        osb.probe();

        osb.getMBeans(mbeans);

        osb.stop();

        System.out.printf("found %d mbeans%n", mbeans.size());
    }
}
