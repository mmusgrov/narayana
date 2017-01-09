package org.jboss.narayana.mgmt.mbean.jts;

import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.ats.internal.arjuna.thread.ThreadActionData;

import com.arjuna.ats.internal.jta.transaction.jts.TransactionImple;
import com.arjuna.ats.internal.jta.transaction.jts.TransactionManagerImple;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import org.jboss.narayana.mgmt.mbean.LogBrowser;
import org.jboss.narayana.mgmt.mbean.jta.DummyXA;
import org.jboss.narayana.mgmt.mbean.jta.FailureXAResource;
import org.jboss.narayana.mgmt.internal.arjuna.LogRecordWrapper;
import org.jboss.narayana.mgmt.internal.arjuna.OSEntryBean;
import org.jboss.narayana.mgmt.internal.arjuna.ObjStoreBrowser;
import org.jboss.narayana.mgmt.internal.arjuna.UidWrapper;
import org.jboss.narayana.mgmt.internal.jta.JTAActionBean;
import org.jboss.narayana.mgmt.mbean.arjuna.OSEntryBeanMBean;
import org.jboss.narayana.mgmt.util.JMXServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.management.InstanceNotFoundException;
import javax.management.JMX;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.transaction.HeuristicMixedException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.arjuna.ats.internal.jts.ORBManager;
import com.arjuna.orbportability.OA;
import com.arjuna.orbportability.ORB;
import com.arjuna.orbportability.RootOA;

class ExtendedFailureXAResource extends FailureXAResource {
	private boolean forgotten;

	@Override
	public void commit(Xid id, boolean onePhase) throws XAException {
		if (!forgotten)
			super.commit(id, onePhase);
	}

	@Override
	public void forget(Xid xid) throws XAException {
		super.forget(xid);
		forgotten = true;
	}
}

public class JTSObjStoreBrowserTest {
    private ORB myORB = null;
    private RootOA myOA = null;

    private ObjStoreBrowser osb;

    private ObjStoreBrowser createObjStoreBrowser() throws Exception {
		ObjStoreBrowser osb = LogBrowser.getBrowser(null).getImpl(); // calls osb.start()

		osb.setType("com.arjuna.ats.arjuna.AtomicAction", "org.jboss.narayana.mgmt.internal.jta.JTAActionBean");

		return osb;
	}

	@Before
    public void beforeTest() throws Exception {
        emptyObjectStore();
        setUpJTS();
        FailureXAResource.resetForgetCounts();
        osb = createObjStoreBrowser();
    }

    @After
    public void afterTest() throws Exception {
        osb.stop();
        tearDownJTS();
    }

    private void setUpJTS () throws Exception {
/*        myORB = ORB.getInstance("test");
        myOA = OA.getRootOA(myORB);

        myORB.initORB(new String[] {}, null);
        myOA.initOA();

        ORBManager.setORB(myORB);
        ORBManager.setPOA(myOA);*/
    }

    private void tearDownJTS () throws Exception {
/*        myOA.destroy();
        myORB.shutdown();*/
    }

/*	@Test
	public void testXAResourceRecordBean() throws Exception {
		com.arjuna.common.tests.simple.EnvironmentBeanTest.testBeanByReflection(new XAResourceRecordBean(new UidWrapper(Uid.nullUid())));
	}*/
/*TODO
	@Test
	public void testCommitMarkableResourceRecordBean() throws Exception {
		com.arjuna.common.tests.simple.EnvironmentBeanTest.testBeanByReflection(new CommitMarkableResourceRecordBean(new UidWrapper(Uid.nullUid())));
	}*/

    /**
     * Test that resources that generate heuristics are instrumented correctly
     * @throws Exception
     */
//    @Test
    public void testMBeanHeuristic () throws Exception
    {
//        FailureXAResource failureXAResource = new FailureXAResource(FailureXAResource.FailLocation.commit); // generates a heuristic on commit

//        getHeuristicMBean(osb, new TransactionImple(), failureXAResource);
    }

    /**
     * Test removing a heuristic participant (each test asserts forget was called (see tryRemove)
     * - if forget succeeds check that the log is removed
     */
    @Test
    public void testParticipantForgetAndRemove () throws Exception
    {
        // generate a heuristic
        HeuristicTestData hd = getHeuristic();

        // try removing the resource (forget calls succeed)
        tryRemove(false, false, hd);

        // since the remove op ignores forget failures it should succeed (meaning that there will be no corresponding MBean)
        assertEquals(0, hd.getHeuristicParticipants().size());
    }

    /**
     * Test removing a heuristic participant (each test asserts forget was called (see tryRemove)
     * - if forget fails and the property to ignore forget failures is set check that the log is removed
     */
    @Test
    public void testParticipantRemovePasses () throws Exception
    {
        // generate a heuristic
        HeuristicTestData hd = getHeuristic();

        // try removing the resource (forget fails and remove ignores forget failures)
        tryRemove(true, true, hd);

        // since the remove op ignores forget failures it should succeed (meaning that there will be no corresponding MBean)
        assertEquals(0, hd.getHeuristicParticipants().size());
    }

    /**
     * Test removing a heuristic participant (each test asserts forget was called (see tryRemove)
     * - if forget fails and the property to ignore forget failures is not set check that the log is not removed
     */
    @Test
    public void testParticipantRemoveFails () throws Exception
    {
        // generate a heuristic
        HeuristicTestData hd = getHeuristic();

        // try removing the resource (forget fails and remove does not ignore forget failures)
        tryRemove(true, false, hd);

        // since the remove op does not ignores forget failures it should fail (meaning that the corresponding MBean still exists)
        assertEquals(1, hd.getHeuristicParticipants().size());
    }

    /**
     * Test removing a transaction with heuristic participants succeeds if the ignores heuristics property is true
     */
    @Test
    public void testTxnRemovePasses () throws Exception
    {
        // generate a heuristic
        HeuristicTestData hd = getHeuristic();

        // ignore forget failures during MBean remove opertaions
        arjPropertyManager.getObjectStoreEnvironmentBean().setIgnoreMBeanHeuristics(true);

        // tell the heuristic resource to fail forget calls
        hd.setRefuseForget(true);

        // invoke the MBean remove operation on the transaction
        hd.txnMBean.remove();

        osb.probe();

        // verify that the txn is no longer instrumented
        assertEquals(0, hd.getTransactionObjectNames().size());


    }

    /**
     * Test removing a transaction with heuristic participants fails if the ignores heuristics property is false
     */
    @Test
    public void testTxnRemoveFails () throws Exception
    {
        // generate a heuristic
        HeuristicTestData hd = getHeuristic();

        // ignore forget failures during MBean remove opertaions
        arjPropertyManager.getObjectStoreEnvironmentBean().setIgnoreMBeanHeuristics(false);

        // tell the heuristic resource to fail forget calls
        hd.setRefuseForget(true);

        // invoke the MBean remove operation on the transaction
        hd.txnMBean.remove();

        osb.probe();

        // verify that the txn is still instrumented
        assertEquals(1, hd.getTransactionObjectNames().size());
    }


    private static final class HeuristicTestData {
        FailureXAResource failureXAResource;
        TransactionManagerImple tm;
        XAResourceRecordBeanMBean resourceBean;
        JTAActionBean txnMBean;
        ObjectName participantBeanName;
        String resourceBeanName;
        String txnBeanName;
        Uid uid;

        HeuristicTestData(TransactionManagerImple tm, FailureXAResource failureXAResource, JTAActionBean txnMBean,
                          XAResourceRecordBeanMBean resourceBean,  ObjectName participantBeanName,
                          String txnBeanName, String resourceBeanName) {
            this.failureXAResource = failureXAResource;
            this.tm = tm;
            this.resourceBean = resourceBean;
            this.txnBeanName = txnBeanName;
            this.txnMBean = txnMBean;
            this.participantBeanName = participantBeanName;
            this.resourceBeanName = resourceBeanName;
        }

        Set<ObjectName> getTransactionObjectNames() throws MalformedObjectNameException {
            Set<ObjectName> names = JMXServer.getAgent().queryNames(txnBeanName, null);

            return names != null ? names : new HashSet<>();
        }

        Set<ObjectName> getHeuristicParticipants() throws MalformedObjectNameException {
            Set<ObjectName> names =  JMXServer.getAgent().queryNames(resourceBeanName, null);

            return names != null ? names : new HashSet<>();
        }

        OSEntryBeanMBean getParticipantMBean() throws InstanceNotFoundException {
            MBeanServer server = JMXServer.getAgent().getServer();

            return JMX.newMBeanProxy(server, participantBeanName, OSEntryBeanMBean.class);
        }

        void setRefuseForget(boolean refuse) {
            failureXAResource.setRefuseForget(failureXAResource.getXid(), refuse);
        }

        public void update(Uid uid, TransactionManagerImple tm, FailureXAResource failureXAResource, JTAActionBean txnMBean,
                           XAResourceRecordBeanMBean resourceBean, ObjectName participantBeanName, String txnBeanName, String resourceBeanName) {
            this.uid = uid;
            this.tm = tm;
            this.failureXAResource = failureXAResource;
            this.txnMBean = txnMBean;
            this.resourceBean = resourceBean;
            this.participantBeanName = participantBeanName;
            this.txnBeanName = txnBeanName;
            this.resourceBeanName = resourceBeanName;
        }
    }

    private HeuristicTestData getHeuristic() throws Exception
    {
        FailureXAResource failureXAResource = new FailureXAResource(FailureXAResource.FailLocation.commit); // generates a heuristic on commit
        TransactionManagerImple tm = new TransactionManagerImple();
        HeuristicTestData hd = new HeuristicTestData(tm, failureXAResource, null, null, null, null, null);

//        TransactionImple tx = new TransactionImple();
        XAResourceRecordBeanMBean resourceBean = getHeuristicMBean(osb, hd, failureXAResource);
        JTAActionBean txnMBean = getTransactionBean(osb, hd, true);
        Set<ObjectName> participants;
        String resourceBeanName;
        String txnBeanName;

        assertNotNull(txnMBean);
        assertNotNull(resourceBean);

        txnBeanName = String.format("jboss.jta:type=ObjectStore,itype=%s,uid=%s",
                txnMBean.type(), txnMBean.getId().replace(':', '_'));

        resourceBeanName = String.format("%s,puid=%s",
                txnBeanName, resourceBean.getId().replace(':', '_'));

        participants = JMXServer.getAgent().queryNames(resourceBeanName, null);

        assertEquals(1, participants.size());

        hd.update(hd.uid, tm, failureXAResource, txnMBean, resourceBean, participants.iterator().next(),txnBeanName, resourceBeanName);

        return hd;
    }

    private HeuristicTestData tryRemove(boolean failForget, boolean ignoreMBeanHeuristics, HeuristicTestData hd)
            throws MBeanException, MalformedObjectNameException, InstanceNotFoundException {
        ObjectStoreEnvironmentBean osEnv = arjPropertyManager.getObjectStoreEnvironmentBean();

        osEnv.setIgnoreMBeanHeuristics(ignoreMBeanHeuristics);

        hd.failureXAResource.setRefuseForget(hd.failureXAResource.getXid(), failForget);

        // remove the bean via a JMX proxy
        hd.getParticipantMBean().remove();

        // an equivalent alternative would have been to call remove directly on the bean
        //hd.resourceBean.remove();

        // assert that forget was called on the resource
        assertEquals(1, hd.failureXAResource.getForgetCount(hd.failureXAResource.getXid()));

        osb.probe();

        return hd;
    }

    private Uid generateHeuristic(TransactionManagerImple tm, FailureXAResource failureXAResource) throws Exception {
        ThreadActionData.purgeActions();
        XAResource[] resources = {
                new DummyXA(false),
                failureXAResource
        };

        tm.begin();

        TransactionImple tx = (TransactionImple) tm.getTransaction();
        Uid uid = tx.get_uid();

        // enlist the XA resources into the transaction
        for (XAResource resource : resources)
            tm.getTransaction().enlistResource(resource);

        try {
            tm.commit();

            fail("Expected a mixed heuristic");
        } catch (final HeuristicMixedException expected) {
        }

        return uid;
    }

    private JTAActionBean getTransactionBean(ObjStoreBrowser osb, HeuristicTestData hd, boolean present) throws SystemException {
        // there should be one MBean corresponding to the Transaction tx
        UidWrapper w = osb.findUid(hd.uid);

        if (!present) {
            assertNull(w);
            return null;
        }

        assertNotNull(w);
        OSEntryBean ai = w.getMBean();
        assertNotNull(ai);

        // the MBean should wrap a JTAActionBean
        assertTrue(ai instanceof JTAActionBean);

        return (JTAActionBean) ai;
    }

    private XAResourceRecordBeanMBean getHeuristicMBean(ObjStoreBrowser osb, HeuristicTestData hd, FailureXAResource failureXAResource) throws Exception {
        hd.uid = generateHeuristic(hd.tm, failureXAResource);

        osb.probe();
        // there should be one MBean corresponding to the Transaction
        JTAActionBean actionBean = getTransactionBean(osb, hd, true);

        assertNotNull(actionBean);

        // and the transaction should contain only one participant (namely the FailureXAResource that generated the heuristic):
        Collection<LogRecordWrapper> participants = actionBean.getParticipants();

        assertEquals(1, participants.size());
        assertNotNull(failureXAResource.getXid());

        LogRecordWrapper participant = participants.iterator().next();

        assertTrue(participant.isHeuristic());
        assertTrue(participant instanceof XAResourceRecordBeanMBean);

        return (XAResourceRecordBeanMBean) participant;
    }

    public void emptyObjectStore()
    {
        String objectStoreDirName = BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).getObjectStoreDir();

        System.out.println("Emptying " + objectStoreDirName);

        File objectStoreDir = new File(objectStoreDirName);

        removeContents(objectStoreDir);
    }

    public boolean removeContents(File directory)
    {
        boolean deleteParent = true;
        if ((directory != null) &&
                directory.isDirectory() &&
                (!directory.getName().equals("")) &&
                (!directory.getName().equals("/")) &&
                (!directory.getName().equals("\\")) &&
                (!directory.getName().equals(".")) &&
                (!directory.getName().equals("target/generated-sources")))
        {
            File[] contents = directory.listFiles();

            if (contents != null) {
                boolean canDelete = true;
                for (File content : contents) {
                    if (content.isDirectory()) {
                        if (!content.getName().equals("RecoveryCoordinator")) {
                            canDelete = removeContents(content) && canDelete;

                            if (!canDelete) {
                                deleteParent = false;
                            } else {
                                content.delete();
                            }
                        } else {
                            deleteParent = false;
                        }
                    } else {
                        content.delete();
                    }
                }
            }
        }
        return deleteParent;
    }
}
