package com.arjuna.ats.internal.arjuna.tools.osb;

import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.objectstore.RecoveryStore;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.arjuna.tools.osb.util.JMXServer;
import com.arjuna.ats.internal.arjuna.common.UidHelper;
import com.arjuna.ats.internal.arjuna.tools.osb.ObjStoreBrowser;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

import javax.management.*;

import java.io.File;
import java.io.PrintStream;
import java.util.Map;
import java.util.Set;

public class MBeanHelper {
    private static String MBEAN_PROP1 = "ExposeAllRecordsAsMBeans";

    public static ObjStoreBrowser createObjStoreBrowser(ObjStoreBrowser browser, boolean exposeAllLogs, boolean useJMX) throws OperationsException, ReflectionException, MBeanException {
        if (browser == null) {
            browser = new ObjStoreBrowser();
//            ObjectStoreEnvironmentBean osEnvBean = BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class);

//            osEnvBean.setExposeAllLogRecordsAsMBeans(exposeAllLogs);

            // make sure the object store tooling MBean is ready
            browser.start();
        }

        ObjectName storeMBeanName = new ObjectName(ObjStoreBrowser.STORE_MBEAN_NAME);
//        debugMBean(ObjStoreBrowser.STORE_MBEAN_NAME);

        if (exposeAllLogs) {
            if (useJMX)
                JMXServer.getAgent().getServer().setAttribute(storeMBeanName, new Attribute(MBEAN_PROP1, Boolean.TRUE));
                //JMXServer.getAgent().getServer().invoke(storeMBeanName, "exposeAllRecordsAsMBeans", new Object[]{Boolean.TRUE}, null);
            else
                browser.setExposeAllRecordsAsMBeans(true);
        } else {
            if (useJMX)
                JMXServer.getAgent().getServer().setAttribute(storeMBeanName, new Attribute(MBEAN_PROP1, Boolean.FALSE));
            else
                browser.setExposeAllRecordsAsMBeans(false);
        }

        return browser;
    }

    public static void debugMBean(String objectName) throws MalformedObjectNameException, IntrospectionException, InstanceNotFoundException, ReflectionException {
        MBeanServer mbs = JMXServer.getAgent().getServer();

        Set<ObjectInstance> beans = mbs.queryMBeans(new ObjectName(objectName), null);

        if (beans.isEmpty())
            return;

        ObjectInstance oi = beans.iterator().next();

        printAttributes(System.out, "", mbs, oi);
    }

    private static void printAttributes(PrintStream printStream, String printPrefix, MBeanServer mbs, ObjectInstance oi)
            throws IntrospectionException, InstanceNotFoundException, ReflectionException {
        MBeanInfo info = mbs.getMBeanInfo( oi.getObjectName() );
        MBeanAttributeInfo[] attributeArray = info.getAttributes();
        int i = 0;
        String[] attributeNames = new String[attributeArray.length];

        for (MBeanAttributeInfo ai : attributeArray)
            attributeNames[i++] = ai.getName();

        AttributeList attributes = mbs.getAttributes(oi.getObjectName(), attributeNames);

        for (javax.management.Attribute attribute : attributes.asList()) {
            Object value = attribute.getValue();
            String v =  value == null ? "null" : value.toString();

            printStream.printf("%s%s=%s%n", printPrefix, attribute.getName(), v);
        }

        // Similarly for operations
        MBeanOperationInfo[] opArray = info.getOperations();
        i = 0;
        String[] opNames = new String[opArray.length];

        for (MBeanOperationInfo ai : opArray)
            opNames[i++] = ai.getName();

//        OperationL attributes = mbs.get(oi.getObjectName(), attributeNames);

        for (javax.management.Attribute attribute : attributes.asList()) {
            Object value = attribute.getValue();
            String v =  value == null ? "null" : value.toString();

            printStream.printf("%s%s=%s%n", printPrefix, attribute.getName(), v);
        }
    }

    // Given a set of MBean names find their corresponding Uids
    public static Map<Uid, ObjectName> getUids(Map<Uid, ObjectName> uids, Set<ObjectName> osEntries) {
        MBeanServer mbs = JMXServer.getAgent().getServer();

        for (ObjectName name : osEntries) {
            Object id = getProperty(mbs, name, "Id");

            if (id != null)
                uids.put(new Uid(id.toString()), name);
        }

        return uids;
    }

    // look up an MBean property
    public static Object getProperty(MBeanServer mbs, ObjectName name, String id) {
        try {
            return mbs.getAttribute(name, id);
        } catch (AttributeNotFoundException e) {
            // ok
        } catch (Exception e) {
            System.out.println("Exception looking up attribute " + id + " for object name " + name);
            e.printStackTrace();
        }

        return null;
    }

    // lookup all log records of a given type
    public static Set<Uid> getUids(RecoveryStore recoveryStore, Set<Uid> uids, String type) {
        try {
            InputObjectState states = new InputObjectState();

            if (recoveryStore.allObjUids(type, states) && states.notempty()) {
                boolean finished = false;

                do {
                    Uid uid = UidHelper.unpackFrom(states);

                    if (uid.notEquals(Uid.nullUid())) {
                        uids.add(uid);
                    } else {
                        finished = true;
                    }

                } while (!finished);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return uids;
    }

    public static void emptyObjectStore() {
        String objectStoreDirName = BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).getObjectStoreDir();

        System.out.println("Emptying " + objectStoreDirName);

        File objectStoreDir = new File(objectStoreDirName);

        removeContents(objectStoreDir);
    }

    public static void removeContents(File directory)
    {
        if ((directory != null) &&
                directory.isDirectory() &&
                (!directory.getName().equals("")) &&
                (!directory.getName().equals("/")) &&
                (!directory.getName().equals("\\")) &&
                (!directory.getName().equals(".")) &&
                (!directory.getName().equals("..")))
        {
            File[] contents = directory.listFiles();

            if (contents != null) {
                for (File content : contents) {
                    if (content.isDirectory()) {
                        removeContents(content);

                        content.delete();
                    } else {
                        content.delete();
                    }
                }
            }
        }
    }
}
/*
    static final String osMBeanName = "jboss.jta:type=ObjectStore";
    Pattern typePattern = Pattern.compile("itype=(.*?),puid");

    private Map<String, String> getMBeanValues(MBeanServerConnection cnx, ObjectName on, String ... attributeNames)
            throws InstanceNotFoundException, IOException, ReflectionException, IntrospectionException {

        if (attributeNames == null) {
            MBeanInfo info = cnx.getMBeanInfo( on );
            MBeanAttributeInfo[] attributeArray = info.getAttributes();
            int i = 0;
            attributeNames = new String[attributeArray.length];

            for (MBeanAttributeInfo ai : attributeArray)
                attributeNames[i++] = ai.getName();
        }

        AttributeList attributes = cnx.getAttributes(on, attributeNames);
        Map<String, String> values = new HashMap<String, String>();

        for (javax.management.Attribute attribute : attributes.asList()) {
            Object value = attribute.getValue();

            values.put(attribute.getName(), value == null ? "" : value.toString());
        }

        return values;
    }

    private void addTransactions(final Resource parent, Set<ObjectInstance> transactions, MBeanServer mbs)
            throws IntrospectionException, InstanceNotFoundException, IOException,
            ReflectionException, MalformedObjectNameException {

        for (ObjectInstance oi : transactions) {
            String transactionId = oi.getObjectName().getCanonicalName();

            if (!transactionId.contains("puid") && transactionId.contains("itype")) {
                final Resource transaction = new LogStoreResource.LogStoreRuntimeResource(oi.getObjectName());
                final ModelNode model = transaction.getModel();

                Map<String, String> tAttributes = getMBeanValues(
                        mbs,  oi.getObjectName(), LogStoreConstants.TXN_JMX_NAMES);
                String txnId = tAttributes.get("Id");

                addAttributes(model, LogStoreConstants.MODEL_TO_JMX_TXN_NAMES, tAttributes);
                // model.get(LogStoreConstants.JMX_ON_ATTRIBUTE).set(transactionId);

                String participantQuery =  transactionId + ",puid=*";
                Set<ObjectInstance> participants = mbs.queryMBeans(new ObjectName(participantQuery), null);

                addParticipants(transaction, participants, mbs);

                final PathElement element = PathElement.pathElement(LogStoreConstants.TRANSACTIONS, txnId);
                parent.registerChild(element, transaction);
            }
        }
    }

    private Resource probeTransactions(MBeanServer mbs, boolean exposeAllLogs) {
        try {
            ObjectName on = new ObjectName(osMBeanName);

            mbs.setAttribute(on, new javax.management.Attribute("ExposeAllRecordsAsMBeans", Boolean.valueOf(exposeAllLogs)));
            mbs.invoke(on, "probe", null, null);

            Set<ObjectInstance> transactions = mbs.queryMBeans(new ObjectName(osMBeanName +  ",*"), null);

            System.out.printf("probeTransactions: found %d mbeans:%n", transactions.size());
            for (ObjectInstance oi : transactions)
                System.out.printf("\t%s%n", oi.getObjectName().toString());

            final Resource resource = Resource.Factory.create();
            addTransactions(resource, transactions, mbs);
            return resource;

    }*/
