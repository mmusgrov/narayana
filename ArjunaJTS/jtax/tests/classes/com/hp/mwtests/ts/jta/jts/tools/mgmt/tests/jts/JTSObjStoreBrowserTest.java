/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.hp.mwtests.ts.jta.jts.tools.mgmt.tests.jts;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.recoveryPropertyManager;
import com.arjuna.ats.arjuna.coordinator.*;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeManager;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeMap;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.arjuna.recovery.RecoveryDriver;
import com.arjuna.ats.arjuna.recovery.RecoveryManager;

import com.arjuna.ats.internal.arjuna.recovery.AtomicActionRecoveryModule;
import com.arjuna.ats.internal.arjuna.thread.ThreadActionData;

import com.arjuna.ats.internal.jta.recovery.jts.XARecoveryModule;

import com.arjuna.ats.internal.jts.ORBManager;
import com.arjuna.ats.internal.jts.orbspecific.coordinator.ArjunaTransactionImple;
import com.arjuna.ats.jts.common.jtsPropertyManager;
import com.arjuna.orbportability.OA;
import com.arjuna.orbportability.ORB;
import com.hp.mwtests.ts.jta.jts.common.ExtendedCrashRecord;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.JMXServer;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.ObjStoreMBeanON;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.ParticipantStatus;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.tests.common.TestBase;
import org.omg.CORBA.ORBPackage.InvalidName;
import org.omg.CosTransactions.HeuristicHazard;

import javax.management.*;
import java.lang.reflect.Field;
import java.util.Properties;
import java.util.Set;


import org.junit.*;
import static org.junit.Assert.*;

/**
 * Test the the ObjStoreBrowser MBean in a JTS environment.
 *
 * @author Mike Musgrove
 */

public class JTSObjStoreBrowserTest extends TestBase {
	private RecoveryManager rcm;
	private RecoveryDriver rd;

	@BeforeClass
	public static void initOrb() throws InvalidName {
		int recoveryOrbPort = jtsPropertyManager.getJTSEnvironmentBean().getRecoveryManagerPort();
		final Properties p = new Properties();
		p.setProperty("OAPort", "" + recoveryOrbPort);
		p.setProperty("com.sun.CORBA.POA.ORBPersistentServerPort", ""+recoveryOrbPort);
		p.setProperty("com.sun.CORBA.POA.ORBServerId", "" + recoveryOrbPort);

		ORB orb = ORB.getInstance("test");
		OA oa = OA.getRootOA(orb);
		orb.initORB(new String[] {}, p);
		oa.initOA();

		ORBManager.setORB(orb);
		ORBManager.setPOA(oa);
	}

	@AfterClass
	public static void shutdownOrb() {
		ORBManager.getPOA().destroy();
		ORBManager.getORB().shutdown();
		ORBManager.reset();
	}

	@Before
	public void setUp () throws Exception
	{
		recoveryPropertyManager.getRecoveryEnvironmentBean().setRecoveryListener(true);
		recoveryPropertyManager.getRecoveryEnvironmentBean().setPeriodicRecoveryPeriod(1);
		recoveryPropertyManager.getRecoveryEnvironmentBean().setRecoveryBackoffPeriod(1);

		rcm = RecoveryManager.manager();
		rcm.addModule(new XARecoveryModule());
		rcm.addModule(new AtomicActionRecoveryModule());
		rd = new RecoveryDriver(RecoveryManager.getRecoveryManagerPort(),
				recoveryPropertyManager.getRecoveryEnvironmentBean().getRecoveryAddress(), 100000);

		osb.setExposeAllRecordsAsMBeans(true);
//TODO 		TypeRepository.registerTypeHandler(new JTSARHandler());
	}

	@After
	public void tearDown () throws Exception
	{
		rcm.removeAllModules(false);
		rcm.terminate(false);
		Field f = RecoveryManager.class.getDeclaredField("_recoveryManager");
        f.setAccessible(true);
        f.set(rcm, null);
	}

	/*
		 TODO JTS test-compile doesn't pull in com.arjuna.common.tests.simple
	@Test
	public void testXAResourceRecordBean() throws Exception {
		com.arjuna.common.tests.simple.EnvironmentBeanTest.testBeanByReflection(new XAResourceRecordBean(new UidWrapper(Uid.nullUid())));
	}
	*/

	/**
	 * Create an atomic action with two participants, one of which will generate a heuristic during phase 2.
	 * The test will move the heuristic back into the prepared state and trigger recovery to replay phase 2.
	 * The test then asserts that the corresponding MBeans have been unregistered.
	 * @throws Exception if test fails unexpectedly
	 */
	@Test
	public void aaReplayTest() throws Exception {
		AtomicAction A = new AtomicAction();
		ExtendedCrashRecord recs[] = startTest(A);

		int outcome = A.commit();

		assertEquals(ActionStatus.H_HAZARD, outcome);

		finishTest(A, true, recs[1]);
	}

	/**
	 * Similar to @aaReplayTest except that the whole transaction record is removed from the object store
	 * (instead of replaying the record that generates a heuristic).
	 * @throws Exception if test fails unexpectedly
	 */
	@Test
	public void aaRemoveTest() throws Exception {
		AtomicAction A = new AtomicAction();
		ExtendedCrashRecord recs[] = startTest(A);

		int outcome = A.commit();

		assertEquals(ActionStatus.H_HAZARD, outcome);

		finishTest(A, false, recs[1]);
	}

	/**
	 * Similar to aaReplayTest but uses a JTS transaction instead of an AtomicAction
	 * @throws Exception if test fails unexpectedly
	 */
	// TODO for replay to work on JTS participants ExtendedCrashReocrd needs to extend XAResourceRecord
	// TODO @Test
	public void jtsReplayTest() throws Exception {
		ArjunaTransactionImple A = new ArjunaTransactionImple(null);
		ExtendedCrashRecord recs[] = startTest(A);

		int outcome = ActionStatus.COMMITTED;

		try {
			A.commit(true);
		} catch (HeuristicHazard e) {
			outcome = ActionStatus.H_HAZARD;
		}

		assertEquals(ActionStatus.H_HAZARD, outcome);

		finishTest(A, true, recs[1]);
	}

	/**
	 * Similar to aaRemoveTest but uses a JTS transaction instead of an AtomicAction
	 * @throws Exception if test fails unexpectedly
	 */
	@Test
	public void jtsRemoveTest() throws Exception {
		ArjunaTransactionImple A = new ArjunaTransactionImple(null);
		ExtendedCrashRecord recs[] = startTest(A);

		int outcome = ActionStatus.COMMITTED;

		try {
			A.commit(true);
		} catch (HeuristicHazard e) {
			outcome = ActionStatus.H_HAZARD;
		}

		assertEquals(ActionStatus.H_HAZARD, outcome);

		finishTest(A, false, recs[1]);
	}

	// create 2 participants, start the action and enlist both participants
	private ExtendedCrashRecord[] startTest(TwoPhaseCoordinator A) throws Exception {
		ThreadActionData.purgeActions();

		ExtendedCrashRecord recs[] = {
				new ExtendedCrashRecord(ExtendedCrashRecord.CrashLocation.NoCrash, ExtendedCrashRecord.CrashType.Normal),
				new ExtendedCrashRecord(ExtendedCrashRecord.CrashLocation.CrashInCommit, ExtendedCrashRecord.CrashType.HeuristicHazard)
		};

		RecordTypeManager.manager().add(new RecordTypeMap() {
			public Class<? extends AbstractRecord> getRecordClass () { return ExtendedCrashRecord.class;}
			public int getType () {return RecordType.USER_DEF_FIRST0;}
		});

		A.start();

		for (ExtendedCrashRecord rec : recs)
			A.add(rec);
		
		return recs;
	}

	/*
	 * Make sure there is an MBean corresponding to A and that at least one of recs has a heuristic.
	 * Then either remove the action or replay (via the MBean) the record that got the heuristic
	 * checking that the MBeans have all been unregistered from the MBeanServer.
	 */
	private void finishTest(TwoPhaseCoordinator A, boolean replay, ExtendedCrashRecord heuristicRecord) throws Exception {
		osb.probe();

        Set<ObjectName> matchingBeans = JMXServer.getAgent().findMatchingBeans(null, null, "*");

        assertTrue(matchingBeans.size() > 1); // make sure there are at least 2 beans

        String parentName = ObjStoreMBeanON.generateObjectName(A.type(), A.get_uid());
        String participantName= ObjStoreMBeanON.generateParticipantObjectName(heuristicRecord.type(), A.get_uid(), heuristicRecord.order());
		ObjectName parentON = new ObjectName(parentName);
		ObjectName participantObjectNameON = new ObjectName(participantName);

		MBeanServer mbs = JMXServer.getAgent().getServer();

		// there should now be an entry in the object store corresponding to the transaction
        assertTrue(matchingBeans.contains(parentON));
		// and an entry for the ExtendedCrashRecord that got the heuristic
        assertTrue(matchingBeans.contains(participantObjectNameON));

		Object status = mbs.getAttribute(participantObjectNameON, "Status");

		// check that the participant record is a heuristic
		assertTrue(ParticipantStatus.HEURISTIC.name().equals(status.toString()));

		if (replay) {
			heuristicRecord.forget();
			writeProperty(mbs, participantObjectNameON, "Status", ParticipantStatus.PREPARED.name());
			status = getProperty(mbs, participantObjectNameON, "Status");

			assertTrue(ParticipantStatus.PREPARED.name().equals(status.toString()));

		}

		if (!replay) {
			StoreManager.getParticipantStore().remove_committed(A.getSavingUid(), A.type());
		} else {
			/*
			* prompt the recovery manager to have a go at replaying the record that was
			* moved off the heuristic list and back onto the prepared list
			*/
			rd.synchronousScan();
		}

		// another probe should no longer find the record that got the heuristic
		// (since it was either removed or the RecoveryManager replayed the commit
		// phase) so its corresponding MBean will have been unregistered
		osb.probe();

 		matchingBeans = JMXServer.getAgent().findMatchingBeans(null, null, "*");

        assertFalse(matchingBeans.contains(participantObjectNameON));
        assertFalse(matchingBeans.contains(parentON));
	}
}
