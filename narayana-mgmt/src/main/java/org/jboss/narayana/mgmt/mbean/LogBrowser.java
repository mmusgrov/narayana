package org.jboss.narayana.mgmt.mbean;

import com.arjuna.ats.arjuna.common.CoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.RecoveryEnvironmentBean;
import com.arjuna.ats.arjuna.exceptions.ObjectStoreException;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.arjuna.recovery.RecoveryManager;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import org.jboss.narayana.mgmt.internal.arjuna.OSBTypeHandler;
import org.jboss.narayana.mgmt.internal.arjuna.ObjStoreBrowser;
import org.jboss.narayana.mgmt.util.JMXServer;
import com.arjuna.ats.internal.arjuna.objectstore.hornetq.HornetqJournalEnvironmentBean;
import com.arjuna.ats.internal.jts.ORBManager;
import com.arjuna.ats.jts.common.JTSEnvironmentBean;

import com.arjuna.orbportability.OA;
import com.arjuna.orbportability.ORB;
import org.omg.CORBA.ORBPackage.InvalidName;

import javax.management.JMX;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

public class LogBrowser {
    private static Map<StoreKey, LogBrowser> browsers = new HashMap<>();
    private static ORB orb = null;
    private static OA oa;

    private ObjStoreBrowser osb;
    private StoreKey storeKey;

    public static void main(String[] args) throws Exception {
        String storeDir = args.length > 0 ? args[0] : null;
        String storeType = args.length > 1 ? args[1] : null;
        LogBrowser lb;

        if (storeType != null && (storeType.contains("hornetq") || storeType.contains("journal")))
            storeType = StoreKey.StoreType.HornetqObjectStoreAdaptor.name();

        lb = getBrowser(new StoreKey(storeType, storeDir));

        List<String> types = lb.getRecordTypes();

        types.forEach(System.out::println);
    }
    private LogBrowser(StoreKey storeKey, ObjStoreBrowser osb) {
        this.storeKey = storeKey;
        this.osb = osb;
    }

    public static synchronized LogBrowser getBrowser(StoreKey storeKey) throws Exception {
        if (oa == null)
            initOrb();

        if (storeKey == null) {
            storeKey = new StoreKey(
                    BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).getObjectStoreType(),
                    BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).getObjectStoreDir());
        }

        if (!browsers.containsKey(storeKey))
            browsers.put(storeKey, createBrowser(storeKey));

        return browsers.get(storeKey);
    }

    public List<String> getRecordTypes() throws ObjectStoreException {
        List<String> recordTypes  = new ArrayList<String>();

        recordTypes.clear();

        InputObjectState types = new InputObjectState();

        if (StoreManager.getRecoveryStore().allTypes(types)) {
            String typeName;

            do {
                try {
                    typeName = types.unpackString();
                    recordTypes.add(typeName);
                } catch (IOException e1) {
                    typeName = "";
                }
            } while (typeName.length() != 0);
        }

        return recordTypes;
    }

    public String[] getRecordInstancNames(String type) {
        MBeanServer mbs = JMXServer.getAgent().getServer();
        String osMBeanName = "jboss.jta:type=ObjectStore,itype=" + type;
        //Set<ObjectInstance> allTransactions = mbs.queryMBeans(new ObjectName("jboss.jta:type=ObjectStore,*"), null);
        Set<ObjectInstance> transactions;

        try {
            transactions = mbs.queryMBeans(new ObjectName(osMBeanName + ",*"), null);

        } catch (MalformedObjectNameException e) {
            System.out.printf("JMX query exceptionL %s", e.getMessage());
        }

        return new String[0];
    }

    public static <T> T newMBeanProxy(String recordInstancName, Class<T> interfaceClass) throws MalformedObjectNameException {
        MBeanServer server = JMXServer.getAgent().getServer();
        return JMX.newMBeanProxy(server, new ObjectName(recordInstancName), interfaceClass, false);
        // or just go directly via ObjStoreBrowser
    }

    private static LogBrowser createBrowser(StoreKey storeKey) throws Exception {
        setupStore(storeKey);

        ObjStoreBrowser osb = new ObjStoreBrowser(storeKey.getLocation(), new ObjStoreTypeInfo() {

            @Override
            public OSBTypeHandler[] getHandlers() {
                return Stream.of(
                        org.jboss.narayana.mgmt.mbean.arjuna.OSBTypeHandlers.getHandlers(),
                        org.jboss.narayana.mgmt.mbean.jta.OSBTypeHandlers.getHandlers(),
                        org.jboss.narayana.mgmt.mbean.jts.OSBTypeHandlers.getHandlers()
                ).flatMap(Stream::of).toArray(OSBTypeHandler[]::new);
            }
        });
        osb.setExposeAllRecordsAsMBeans(true);
        osb.start(); // only required if we want to use JMX

        return new LogBrowser(storeKey, osb);
    }

    private static void setupStore(StoreKey storeKey) throws Exception {
        String storePath = new File(storeKey.getLocation()).getCanonicalPath();

        validateFile(storePath, true);

        BeanPopulator.getDefaultInstance(RecoveryEnvironmentBean.class).setRecoveryModuleClassNames(Arrays.asList(
                "com.arjuna.ats.internal.jta.recovery.arjunacore.CommitMarkableResourceRecordRecoveryModule",
                "com.arjuna.ats.internal.arjuna.recovery.AtomicActionRecoveryModule",
                "com.arjuna.ats.internal.jta.recovery.arjunacore.XARecoveryModule"
        ));

        BeanPopulator.getDefaultInstance(RecoveryEnvironmentBean.class).setRecoveryActivatorClassNames(null);

        if (storeKey.isJournal()) {
            BeanPopulator.getDefaultInstance(HornetqJournalEnvironmentBean.class).setStoreDir(storePath);
            BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).setObjectStoreType(storeKey.getType());
            BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "communicationStore").setObjectStoreType(storeKey.getType());
        }

        BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).setObjectStoreDir(storePath);
        BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "communicationStore").setObjectStoreDir(storePath);
        BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class).setNodeIdentifier("no-recovery");
    }

    private static File validateFile(String name, boolean isDir) {
        File f = new File(name);

        if (!f.exists() || isDir == f.isFile()) // !(isDir ^ f.isFile())
            throw new IllegalArgumentException("File " + name + " does not exist");

        return f;
    }

    private static void initOrb() throws InvalidName {
/*        final Properties initORBProperties = new Properties();
        initORBProperties.setProperty("com.sun.CORBA.POA.ORBServerId", "1");
        initORBProperties.setProperty("com.sun.CORBA.POA.ORBPersistentServerPort", ""
                + BeanPopulator.getDefaultInstance(JTSEnvironmentBean.class).getRecoveryManagerPort());

        orb = ORB.getInstance("test");
        oa = OA.getRootOA(orb);

        orb.initORB(new String[] {}, initORBProperties);
        oa.initOA();

        ORBManager.setORB(orb);
        ORBManager.setPOA(oa);*/

        // trigger loading of startRecoveryActivators
        RecoveryManager.manager();
    }

    public void dispose() {
        StoreManager.shutdown();

/*        if (osb != null)
            osb.stop();


        if (orb != null) {
            oa.destroy();
            orb.shutdown();
        }*/

        try {
            RecoveryManager.manager().terminate(false); // TODO does this cause a hang
        } catch (Throwable ignore) {
        }

        browsers.remove(storeKey);
    }

    public void probe() throws MBeanException {
        osb.probe();
    }

    public ObjStoreBrowser getImpl() {
        return osb;
    }

    public void setExposeAllRecordsAsMBeans(boolean exposeAllRecordsAsMBeans) {
        osb.setExposeAllRecordsAsMBeans(exposeAllRecordsAsMBeans);
    }
}
