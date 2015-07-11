/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat, Inc. and/or its affiliates,
 * and individual contributors as indicated by the @author tags.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 *
 * (C) 2010,
 * @author JBoss, by Red Hat.
 */
package com.hp.mwtests.ts.jta.jts.tools.mgmt.tests.core;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.recoveryPropertyManager;
import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.arjuna.coordinator.ActionStatus;
import com.arjuna.ats.arjuna.coordinator.RecordType;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeManager;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeMap;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.arjuna.recovery.RecoveryDriver;
import com.arjuna.ats.arjuna.recovery.RecoveryManager;
import com.arjuna.ats.arjuna.tools.osb.mbean.OSEntryBean;
import com.arjuna.ats.arjuna.tools.osb.mbean.ObjStoreItemMBean;
import com.arjuna.ats.internal.arjuna.recovery.AtomicActionRecoveryModule;
import com.arjuna.ats.internal.arjuna.recovery.RecoveryManagerImple;
import com.arjuna.ats.internal.arjuna.tools.log.EditableAtomicAction;
import com.arjuna.ats.internal.arjuna.tools.log.EditableTransaction;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.ParticipantStatus;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.JMXServer;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.tests.common.CrashRecord;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.tests.common.TestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.util.Set;

import static org.junit.Assert.*;

public class ObjStoreBrowserTest extends TestBase {
	private RecoveryManagerImple rcm;
	private RecoveryDriver rd;
	private boolean isWindows;

	@Before
	public void setUp () throws Exception {
		// enable socket based recovery
		recoveryPropertyManager.getRecoveryEnvironmentBean().setRecoveryListener(true);
		recoveryPropertyManager.getRecoveryEnvironmentBean().setPeriodicRecoveryPeriod(1);
		recoveryPropertyManager.getRecoveryEnvironmentBean().setRecoveryBackoffPeriod(1);

		rcm = new RecoveryManagerImple(true);
		rcm.addModule(new AtomicActionRecoveryModule());
		rd = new RecoveryDriver(RecoveryManager.getRecoveryManagerPort(),
				recoveryPropertyManager.getRecoveryEnvironmentBean().getRecoveryAddress(), 100000);

		isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
	}

	@After
	public void tearDown () throws Exception {
		rcm.removeAllModules(false);
		rcm.stop(false);
	}

	@Test
	public void testObjectStoreMgmt() throws Exception {
		com.arjuna.common.tests.simple.EnvironmentBeanTest.testBeanByReflection(osb);
	}

	@Test
	public void basicOSBTest () throws Exception
	{
		osb.probe();

		// there should be record MBeans
		Set<ObjectName> matchingBeans = JMXServer.getAgent().findMatchingBeans(null, null, "*");

		assertEquals(0, matchingBeans.size());

		// listing beans of an invalid type returns null
//		assertNull(osb.probe("InvalidType"));

		// JBTM-1230
		// This does not work on the JDBC object store as this test assumes a previous
		// run has left the "Recovery" entry on disk which won't happen in a JDBC store 
//		// TODO windows
//		if (System.getProperty("os.name").toLowerCase().indexOf("windows") == -1) {
//			// listing beans of a valid type returns an empty list
//			assertNotNull(osb.probe("Recovery"));
//		}
	}

	/**
	 * Create an atomic action with two participants, one of which will generate a heuristic during phase 2.
	 * The test will move the heuristic back into the prepared state and trigger recovery to replay phase 2.
	 * The test then asserts that the corresponding MBeans have been unregistered.
	 * @throws Exception if test fails unexpectedly
	 */
	@Test
	public void aaReplayTest() throws Exception {
		// TODO windows
		if (!isWindows) {
			aaTest(true);
		}
	}

	/**
	 * Similar to aaReplayTest except that the whole transaction record is removed from the object store
	 * (instead of replaying the record that generates a heuristic).
	 * @throws Exception if test fails unexpectedly
	 */
	@Test
	public void aaRemoveTest() throws Exception {
		// TODO windows
		if (!isWindows) {
			aaTest(false);
		}
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

//		debugHeuristic(A, 0);
		// generate MBeans representing the atomic action that was just committed
		osb.setExposeAllRecordsAsMBeans(true);
		osb.probe();

        Set<ObjectName> matchingBeans = JMXServer.getAgent().findMatchingBeans(null, null, "*");

        assertTrue(matchingBeans.size() > 1); // make sure there are at least 2 beans

        String parentName = JMXServer.generateObjectName(A.type(), A.get_uid());
        String participantName= JMXServer.generateParticipantObjectName(recs[1].type(), A.get_uid(), recs[1].order());
		ObjectName parentON = new ObjectName(parentName);
		ObjectName participantObjectNameON = new ObjectName(participantName);

		MBeanServer mbs = JMXServer.getAgent().getServer();

        assertTrue(matchingBeans.contains(parentON));
        assertTrue(matchingBeans.contains(participantObjectNameON));

//		ObjectInstance poi = JMXServer.getAgent().getServer().getObjectInstance(participantObjectNameON);

//		List<Attribute> attributeList = getProperties(mbs, participantObjectNameON);

		Object id = getProperty(mbs, participantObjectNameON, "Id");
		Object status = getProperty(mbs, participantObjectNameON, "Status");

		assertEquals(recs[1].get_uid().fileStringForm(), id.toString());
		assertTrue(ParticipantStatus.HEURISTIC.name().equals(status));

		writeProperty(mbs, participantObjectNameON, "Status", ParticipantStatus.PREPARED.name());

		status = getProperty(mbs, participantObjectNameON, "Status");
		assertTrue(ParticipantStatus.PREPARED.name().equals(status));

		if (!replay) {
			// remove the whole record
			StoreManager.getParticipantStore().remove_committed(A.getSavingUid(), A.type());
 		} else {
			/*
			 * prompt the recovery manager to replay the record that was
			 * moved off the heuristic list and back onto the prepared list
			 */
			rd.synchronousScan();
			Thread.sleep(1000); // odd without the delay running under Jacoco fails
		}

		/*
		 * Since the recovery scan (or explicit remove request) will have successfully removed the record from
		 * the object store another probe should cause the MBean representing the record to be unregistered
		 */
		osb.probe();

		matchingBeans = JMXServer.getAgent().findMatchingBeans(null, null, "*");

        assertFalse(matchingBeans.contains(parentON));
        assertFalse(matchingBeans.contains(participantObjectNameON));

		osb.stop();
	}

	// define an MBean interface for use in the next test
	public interface NotAnotherMBean extends ObjStoreItemMBean {}

	@Test
	public void testJMXServer() throws Exception {

		class NonCompliantBean implements NotAnotherMBean {}

		OSEntryBean bean;
		String validName = "jboss.jta:type=TestObjectStore";

		osb.probe();

		bean = new OSEntryBean();

		// MalformedObjectNameException
		assertNull(JMXServer.getAgent().registerMBean("InvalidName", bean));
		assertFalse(JMXServer.getAgent().unregisterMBean("InvalidName"));

		// InstanceNotFoundException
		assertFalse(JMXServer.getAgent().unregisterMBean(validName));

		// NotCompliantMBeanException
		assertNull(JMXServer.getAgent().registerMBean(validName, new NonCompliantBean()));

		// Do it right this time
		int cnt = JMXServer.getAgent().queryNames(validName, null).size();
		assertNotNull(JMXServer.getAgent().registerMBean(validName, bean));
		assertEquals(cnt + 1, JMXServer.getAgent().queryNames(validName, null).size());

		// InstanceAlreadyExistsException
		assertNull(JMXServer.getAgent().registerMBean(validName, bean));

		// Make sure unregistering a valid bean works
		assertTrue(JMXServer.getAgent().unregisterMBean(validName));
		assertEquals(0, JMXServer.getAgent().queryNames(validName, null).size());
	}
}
