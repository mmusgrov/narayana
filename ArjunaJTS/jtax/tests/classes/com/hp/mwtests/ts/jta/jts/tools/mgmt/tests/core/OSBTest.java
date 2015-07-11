package com.hp.mwtests.ts.jta.jts.tools.mgmt.tests.core;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.arjuna.coordinator.ActionStatus;
import com.arjuna.ats.arjuna.coordinator.RecordType;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeManager;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeMap;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.JMXServer;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.tests.common.CrashRecord;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.tests.common.TestBase;
import org.junit.Test;

import javax.management.ObjectName;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OSBTest extends TestBase {
    @Test
    public void test2() throws Exception {
        aaTest(false);
    }

	public void aaTest(boolean replay) throws Exception {
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

        osb.setExposeAllRecordsAsMBeans(true);
        osb.probe();

        // there should be one MBean corresponding to the AtomicAction A
        // and there should be one MBean corresponding to the CrashRecord that got the heuristic

        Set<ObjectName> matchingBeans = JMXServer.getAgent().findMatchingBeans(null, null, "*");

        assertTrue(matchingBeans.size() > 1); // make sure ther are at least 2 beans

        String parentON = JMXServer.generateObjectName(A.type(), A.get_uid());
        String participantObjectNameON = JMXServer.generateParticipantObjectName(recs[1].type(), A.get_uid(), recs[1].get_uid());

        assertTrue(matchingBeans.contains(new ObjectName(parentON)));
        assertTrue(matchingBeans.contains(new ObjectName(participantObjectNameON)));
    }
}
