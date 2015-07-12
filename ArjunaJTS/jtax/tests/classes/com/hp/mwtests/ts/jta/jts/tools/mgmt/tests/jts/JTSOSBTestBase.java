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

import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.arjuna.coordinator.RecordType;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeManager;
import com.arjuna.ats.arjuna.coordinator.abstractrecord.RecordTypeMap;

import com.arjuna.ats.internal.arjuna.thread.ThreadActionData;
import com.arjuna.ats.internal.jta.transaction.jts.subordinate.jca.coordinator.ServerTransaction;
import com.arjuna.ats.internal.jts.ORBManager;
import com.arjuna.ats.internal.jts.orbspecific.coordinator.ArjunaTransactionImple;
import com.arjuna.ats.internal.jts.recovery.transactions.AssumedCompleteHeuristicServerTransaction;
import com.arjuna.ats.internal.jts.recovery.transactions.AssumedCompleteServerTransaction;
import com.arjuna.ats.jts.common.jtsPropertyManager;
import com.arjuna.orbportability.OA;
import com.arjuna.orbportability.ORB;
import com.hp.mwtests.ts.jta.jts.common.ExtendedCrashRecord;

import com.hp.mwtests.ts.jta.jts.tools.mgmt.*;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.jts.ServerTransactionHandler;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.tests.common.TestBase;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.omg.CORBA.ORBPackage.InvalidName;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Mike Musgrove
 */
public class JTSOSBTestBase extends TestBase {
	@BeforeClass
	public static void beforeClass() {
		RecordTypeManager.manager().add(new RecordTypeMap() {
			public Class<? extends AbstractRecord> getRecordClass() {
				return ExtendedCrashRecord.class;
			}

			public int getType() {
				return RecordType.USER_DEF_FIRST0;
			}
		});
	}

	@BeforeClass
	public static void initOrb() throws InvalidName {
		int recoveryOrbPort = jtsPropertyManager.getJTSEnvironmentBean().getRecoveryManagerPort();
		final Properties p = new Properties();
		p.setProperty("OAPort", ""+recoveryOrbPort);
		p.setProperty("com.sun.CORBA.POA.ORBPersistentServerPort", ""+recoveryOrbPort);
		p.setProperty("com.sun.CORBA.POA.ORBServerId", ""+recoveryOrbPort);

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
	public void before() {
		super.before();
		osb.viewSubordinateAtomicActions(true);
		osb.setExposeAllRecordsAsMBeans(true);

		TypeRepository.registerTypeHandler(new ServerTransactionHandler(AssumedCompleteHeuristicServerTransaction.typeName()));
		TypeRepository.registerTypeHandler(new ServerTransactionHandler(AssumedCompleteServerTransaction.typeName()));
		TypeRepository.registerTypeHandler(new ServerTransactionHandler(ServerTransaction.typeName()));
		TypeRepository.registerTypeHandler(new BAHandlerImpl(ArjunaTransactionImple.typeName()));

	}

	protected void assertBeanWasCreated(ArjunaTransactionImple txn) throws MalformedObjectNameException {
		generatedHeuristicHazard(txn);

		osb.probe();

		Set<ObjectName> matchingBeans = JMXServer.getAgent().findMatchingBeans(null, null, "*");

		assertTrue(matchingBeans.size() > 1); // make sure there are at least 2 beans

		String parentName = ObjStoreMBeanON.generateObjectName(txn.type(), txn.get_uid());
		ObjectName parentON = new ObjectName(parentName);

		// there should now be an entry in the object store corresponding to the transaction
		assertTrue(matchingBeans.contains(parentON));
	}

	protected void generatedHeuristicHazard(ArjunaTransactionImple txn) {
		ThreadActionData.purgeActions();

		ExtendedCrashRecord recs[] = {
				new ExtendedCrashRecord(ExtendedCrashRecord.CrashLocation.NoCrash, ExtendedCrashRecord.CrashType.Normal),
				new ExtendedCrashRecord(ExtendedCrashRecord.CrashLocation.CrashInCommit, ExtendedCrashRecord.CrashType.HeuristicHazard)
		};

		txn.start();

		for (ExtendedCrashRecord rec : recs)
			txn.add(rec);

		txn.end(false);
	}
}
