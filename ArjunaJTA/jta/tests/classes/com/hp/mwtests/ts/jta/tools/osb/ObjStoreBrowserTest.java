package com.hp.mwtests.ts.jta.tools.osb;

//import com.arjuna.ats.arjuna.tools.osb.mbean.LogRecordWrapper;
//import com.arjuna.ats.arjuna.tools.osb.mbean.OSEntryBean;
//import com.arjuna.ats.arjuna.tools.osb.mbean.ObjStoreBrowser;
//import com.arjuna.ats.arjuna.tools.osb.mbean.UidWrapper;
import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.ats.arjuna.tools.osb.mbean.LogRecordWrapperMXBean;
import com.arjuna.ats.internal.arjuna.thread.ThreadActionData;
//import com.arjuna.ats.internal.jta.tools.osb.mbean.jta.CommitMarkableResourceRecordBean;
//import com.arjuna.ats.internal.jta.tools.osb.mbean.jta.JTAActionBean;
//import com.arjuna.ats.internal.jta.tools.osb.mbean.jta.XAResourceRecordBean;
import com.arjuna.ats.internal.arjuna.tools.osb.ActionBeanHandler;
import com.arjuna.ats.internal.arjuna.tools.osb.ObjStoreBrowser;
import com.arjuna.ats.internal.arjuna.tools.osb.TypeRepository;
        import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.MBeanAccessor;
        import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.ParticipantStatus;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionImple;
import com.arjuna.ats.jta.common.jtaPropertyManager;
import com.arjuna.ats.jta.tools.mbeans.JTAActionBeanHandler;
import com.hp.mwtests.ts.jta.common.DummyXA;
import com.hp.mwtests.ts.jta.common.FailureXAResource;
import org.junit.BeforeClass;
import org.junit.Test;
import javax.transaction.HeuristicMixedException;
import javax.transaction.xa.XAResource;
import java.io.File;
import java.util.Set;

import static org.junit.Assert.*;

public class ObjStoreBrowserTest {
    /**
     * create an MBean to represent an ObjectStore
     * @return An object that maintains MBeans representing completing transactions
     */
    private ObjStoreBrowser createObjStoreBrowser() {
        ObjStoreBrowser osb = new ObjStoreBrowser();

        // define which object store types we are prepared to represent by mbeans
		TypeRepository.registerTypeHandler(new ActionBeanHandler());
        TypeRepository.registerTypeHandler(new JTAActionBeanHandler());


//        osb.setType("com.arjuna.ats.arjuna.AtomicAction", "com.arjuna.ats.arjuna.tools.osb.mbean.ActionBean");
//		  osb.setType("com.arjuna.ats.arjuna.AtomicAction", "com.arjuna.ats.internal.jta.tools.osb.mbean.jta.JTAActionBean");
        return osb;
    }

    @BeforeClass
    public static void setUp() {
        jtaPropertyManager.getJTAEnvironmentBean().setXAResourceRecordWrappingPlugin(new XAResourceRecordWrappingPluginImpl());
    }

/*    @Test
    public void testXAResourceRecordBean() throws Exception {
        com.arjuna.common.tests.simple.EnvironmentBeanTest.testBeanByReflection(new XAResourceRecordBean(new UidWrapper(Uid.nullUid())));
    }

    @Test
    public void testCommitMarkableResourceRecordBean() throws Exception {
        com.arjuna.common.tests.simple.EnvironmentBeanTest.testBeanByReflection(new CommitMarkableResourceRecordBean(new UidWrapper(Uid.nullUid())));
    }*/

    @Test
    public void testMBeanHeuristic () throws Exception
    {
        ThreadActionData.purgeActions();
        ObjStoreBrowser osb = createObjStoreBrowser();
        XAResource[] resources = {
                new DummyXA(false),
                new FailureXAResource(FailureXAResource.FailLocation.commit) // generates a heuristic on commit
        };

        TransactionImple tx = new TransactionImple(0);

        // enlist the XA resources into the transaction
        for (XAResource resource : resources) {
            tx.enlistResource(resource);
        }

        try {
            tx.commit();

            fail("Expected a mixed heuristic");
        } catch (final HeuristicMixedException ex) {
        }

        osb.start();
        osb.probe();
        // there should be one MBean corresponding to the Transaction
		NamedOSEntryBeanMXBean w = osb.findUid(tx.get_uid());
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

        osb.stop();
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
