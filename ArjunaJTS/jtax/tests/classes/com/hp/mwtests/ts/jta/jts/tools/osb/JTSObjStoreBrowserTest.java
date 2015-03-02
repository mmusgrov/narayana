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
package com.hp.mwtests.ts.jta.jts.tools.osb;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.ats.arjuna.common.recoveryPropertyManager;
import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.arjuna.coordinator.ActionStatus;
import com.arjuna.ats.arjuna.coordinator.RecordType;
import com.arjuna.ats.arjuna.coordinator.TwoPhaseCoordinator;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeManager;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeMap;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.arjuna.recovery.RecoveryDriver;
import com.arjuna.ats.arjuna.recovery.RecoveryManager;

import com.arjuna.ats.arjuna.tools.osb.mbean.LogRecordWrapperMXBean;
import com.arjuna.ats.arjuna.tools.osb.util.JMXServer;
import com.arjuna.ats.internal.arjuna.recovery.AtomicActionRecoveryModule;
import com.arjuna.ats.internal.arjuna.thread.ThreadActionData;
import com.arjuna.ats.internal.arjuna.tools.osb.ObjStoreBrowser;
import com.arjuna.ats.internal.arjuna.tools.osb.TypeRepository;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.MBeanAccessor;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.ParticipantStatus;
import com.arjuna.ats.internal.jta.recovery.jts.XARecoveryModule;
import com.arjuna.ats.internal.jta.tools.osb.mbean.jts.osb.JTSARHandler;
import com.arjuna.ats.internal.jts.ORBManager;
import com.arjuna.ats.internal.jts.orbspecific.coordinator.ArjunaTransactionImple;
import com.arjuna.ats.internal.jts.resources.ExtendedResourceRecord;
import com.arjuna.ats.jts.common.jtsPropertyManager;
import com.arjuna.orbportability.OA;
import com.arjuna.orbportability.ORB;
import com.hp.mwtests.ts.jta.jts.common.ExtendedCrashRecord;
import com.hp.mwtests.ts.jta.jts.common.TestBase;
import org.junit.*;
import org.omg.CORBA.ORBPackage.InvalidName;
import org.omg.CosTransactions.HeuristicHazard;

import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Test the the ObjStoreBrowser MBean in a JTS environment.
 *
 * @author Mike Musgrove
 */

/**
 * @deprecated as of 5.0.5.Final In a subsequent release we will change packages names in order to 
 * provide a better separation between public and internal classes.
 */
@Deprecated // in order to provide a better separation between public and internal classes.
public class JTSObjStoreBrowserTest extends TestBase {
	private RecoveryManager rcm;
	private RecoveryDriver rd;
	private ObjStoreBrowser osb;

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
		clearObjectStore();
		recoveryPropertyManager.getRecoveryEnvironmentBean().setRecoveryListener(true);
		recoveryPropertyManager.getRecoveryEnvironmentBean().setPeriodicRecoveryPeriod(1);
		recoveryPropertyManager.getRecoveryEnvironmentBean().setRecoveryBackoffPeriod(1);

		rcm = RecoveryManager.manager();
		rcm.addModule(new XARecoveryModule());
		rcm.addModule(new AtomicActionRecoveryModule());
		rd = new RecoveryDriver(RecoveryManager.getRecoveryManagerPort(),
				recoveryPropertyManager.getRecoveryEnvironmentBean().getRecoveryAddress(), 100000);

		TypeRepository.registerTypeHandler(new JTSARHandler());
		osb = new ObjStoreBrowser();
		osb.start();
	}

	@After
	public void tearDown () throws Exception
	{
		rcm.removeAllModules(false);
		rcm.terminate(false);
		Field f = RecoveryManager.class.getDeclaredField("_recoveryManager");
        f.setAccessible(true);
        f.set(rcm, null);

		osb.stop();
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

		finishTest(A, true, recs);
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

		finishTest(A, false, recs);
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

		finishTest(A, true, recs);
	}

    /**
     * Test that MBeans corresponding to JTS record types are created
     * @throws Exception
     */
    @Test
    public void jtsMBeanTest() throws Exception {
        ArjunaTransactionImple A = new ArjunaTransactionImple(null);

        startTest(A);

        try {
            A.commit(true);
            fail("transaction commit should have produced a heuristic hazzard");
        } catch (HeuristicHazard e) {
        }

        osb.probe();


        // there should be one MBean corresponding to the Transaction
		NamedOSEntryBeanMXBean w = osb.findUid(A.get_uid());
		assertNotNull(w);

		Set<MBeanAccessor> participants = osb.findParticipantProxies(w, true);

		// and there should be one MBean corresponding to the CrashRecord that got the heuristic:
		assertEquals(1, participants.size());

        for (MBeanAccessor participant : participants) {
            LogRecordWrapperMXBean proxy = participant.getProxy();

            // validate the heuristic status
            assertTrue(participant.getAttributeValue("Status").equals(ParticipantStatus.HEURISTIC.name()));
            assertTrue(proxy.getStatus().equals(ParticipantStatus.HEURISTIC.name())); // check it via the proxy too
//            assertTrue(proxy.isHeuristic());

            // put the participant back onto the pending list
            proxy.setStatus(ParticipantStatus.PREPARED.name());

            // and check that the record is no longer in a heuristic state
            assertFalse(proxy.getStatus().equals(ParticipantStatus.HEURISTIC.name()));
        }

        // there should now be an MBean entry corresponding to a JTS record, read it via JMX:
        MBeanServer mbs = JMXServer.getAgent().getServer();
        Set<ObjectInstance> transactions = mbs.queryMBeans(new ObjectName("jboss.jta:type=ObjectStore,*"), null);
        boolean foundJTSType = false;
        Pattern pattern = Pattern.compile("itype=(.*?),");

        for (ObjectInstance oi : transactions) {
            String id = oi.getObjectName().getCanonicalName();
            Matcher matcher = pattern.matcher(id);

            while (matcher.find())
                foundJTSType = true; // matched type is in matcher.group(1)
        }

        assertTrue("MBean for JTS record type not found", foundJTSType);
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

		finishTest(A, false, recs);
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
	private void finishTest(TwoPhaseCoordinator A, boolean replay, ExtendedCrashRecord ... recs) throws Exception {
		// there should now be an entry in the object store containing two participants
		osb.probe();

		// there should be one MBean corresponding to the AtomicAction A
		NamedOSEntryBeanMXBean w = osb.findUid(A.get_uid());
		assertNotNull(w);

		// and there should be one MBean corresponding to the CrashRecord that got the heuristic:
        Set<MBeanAccessor> participants = osb.findParticipantProxies(w, true);

		assertEquals(1, participants.size());

		MBeanAccessor participant = participants.iterator().next();
		LogRecordWrapperMXBean proxy = null;

		for (ExtendedCrashRecord rec : recs) {
			if (rec.get_uid().stringForm().equals(participant.getProxy().getId())) {
				proxy = participant.getProxy();

				// validate the heuristic status
				assertTrue(participant.getAttributeValue("Status").equals(ParticipantStatus.HEURISTIC.name()));
				assertTrue(proxy.getStatus().equals(ParticipantStatus.HEURISTIC.name())); // check it via the proxy too

				if (replay) {
					rec.forget();
					// put the participant back onto the pending list
					proxy.setStatus(ParticipantStatus.PREPARED.name());

					// and check that the record is no longer in a heuristic state
					assertFalse(proxy.getStatus().equals(ParticipantStatus.HEURISTIC.name()));
				}
			}

		}

		// assert that an MBean corresponding to one of the ExtendedCrashRecords was found
		assertNotNull(proxy);

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

		// look up the MBean and verify that it no longer exists
		w = osb.findUid(A.get_uid());
		assertNull(w);
	}

	private void clearObjectStore() {
		final String objectStorePath = arjPropertyManager.getObjectStoreEnvironmentBean().getObjectStoreDir();
		final File objectStoreDirectory = new File(objectStorePath);

		clearDirectory(objectStoreDirectory);
	}

	private void clearDirectory(final File directory) {
		final File[] files = directory.listFiles();

		if (files != null) {
			for (final File file : files) {
				if (file.isDirectory()) {
					clearDirectory(file);
				}

				file.delete();
			}
		}
	}
}
