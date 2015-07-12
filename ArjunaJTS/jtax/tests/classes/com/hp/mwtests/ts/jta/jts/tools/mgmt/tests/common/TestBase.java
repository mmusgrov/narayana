package com.hp.mwtests.ts.jta.jts.tools.mgmt.tests.common;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.ats.arjuna.objectstore.RecoveryStore;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.internal.arjuna.common.UidHelper;
import com.arjuna.ats.internal.arjuna.tools.osb.ARTypeHandler;
import com.arjuna.ats.internal.arjuna.tools.osb.TypeRepository;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.JMXServer;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.ObjStoreMBeanON;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.ObjStoreMgmt;
import org.junit.After;
import org.junit.Before;

import javax.management.*;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestBase {
    private static String MBEAN_PROP1 = "ExposeAllRecordsAsMBeans";
    protected ObjStoreMgmt osb;

    @Before
    public void before() {
        clearObjectStore();
        osb = new ObjStoreMgmt();
        osb.start();
    }

    @After
    public void after() {
        osb.stop();
        clearObjectStore();
    }

    public ObjStoreMgmt getOSB(ARTypeHandler... handlers) {
        TypeRepository.registerTypeHandler(handlers);
        ObjStoreMgmt osb = new ObjStoreMgmt();

        osb.start();

        return osb;
    }

    public ObjStoreMgmt getOSB(boolean exposeAllLogs, boolean useJMX) throws OperationsException, ReflectionException, MBeanException {
        ObjectName storeMBeanName = new ObjectName(ObjStoreMBeanON.STORE_MBEAN_NAME);
        ObjStoreMgmt browser = new ObjStoreMgmt();

        browser.start();

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

    // look up some MBean properties
    public static List<Attribute> getProperties(MBeanServer mbs, ObjectName name)
            throws IntrospectionException, InstanceNotFoundException, ReflectionException {

        ObjectInstance oi = JMXServer.getAgent().getServer().getObjectInstance(name);
        MBeanInfo info = mbs.getMBeanInfo( oi.getObjectName() );
        MBeanAttributeInfo[] attributeArray = info.getAttributes();
        int i = 0;
        String[] attributeNames = new String[attributeArray.length];

        for (MBeanAttributeInfo ai : attributeArray)
            attributeNames[i++] = ai.getName();

        AttributeList attributeList = mbs.getAttributes(oi.getObjectName(), attributeNames);

        return attributeList.asList();
    }

    public void writeProperty(MBeanServer mbs, ObjectName instanceName, String propertyName, Object propertyValue)
            throws AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException, InvalidAttributeValueException {
        Attribute attribute = new Attribute(propertyName, propertyValue);

        mbs.setAttribute(instanceName, attribute);
	}

/*    public static List<Attribute> getOperations(MBeanServer mbs, ObjectName name)
            throws IntrospectionException, InstanceNotFoundException, ReflectionException {
        ObjectInstance oi = JMXServer.getAgent().getServer().getObjectInstance(name);
        MBeanInfo info = mbs.getMBeanInfo( oi.getObjectName() );

        // Similarly for operations
        MBeanOperationInfo[] opArray = info.getOperations();
        int i = 0;
        String[] opNames = new String[opArray.length];

        for (MBeanOperationInfo ai : opArray)
            opNames[i++] = ai.getName();
    }*/

    public void clearObjectStore() {
        final String objectStorePath = arjPropertyManager.getObjectStoreEnvironmentBean().getObjectStoreDir();
        final File objectStoreDirectory = new File(objectStorePath);

        clearDirectory(objectStoreDirectory);
    }

    public void clearDirectory(final File directory) {
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
