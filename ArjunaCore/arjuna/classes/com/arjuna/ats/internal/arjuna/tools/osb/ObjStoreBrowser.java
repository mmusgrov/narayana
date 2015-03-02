package com.arjuna.ats.internal.arjuna.tools.osb;

import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.Uid;

import com.arjuna.ats.arjuna.exceptions.ObjectStoreException;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.arjuna.tools.osb.mbean.LogRecordWrapperMXBean;
import com.arjuna.ats.arjuna.tools.osb.mbean.OSEntryBeanMXBean;
import com.arjuna.ats.arjuna.tools.osb.mbean.ObjStoreBrowserMXBean;
import com.arjuna.ats.arjuna.tools.osb.util.JMXServer;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.MBeanAccessor;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.MBeanAccessorException;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

import javax.management.*;
import java.io.PrintStream;
import java.util.*;

public class ObjStoreBrowser implements ObjStoreBrowserMXBean, NotificationListener {
    public static final String STORE_MBEAN_NAME = "jboss.jta:type=ObjectStore";

    private TypeRepository types = new TypeRepository();
    private Map<Uid, NamedOSEntryBeanMXBean> mbeans = new HashMap<>();
    private boolean exposeAllLogRecords;

    public void getMBeans(Collection<OSEntryBeanMXBean> beans) {
        beans.addAll(mbeans.values());
    }

    public ObjStoreBrowser() {
        ObjectStoreEnvironmentBean osEnvBean = BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class);

        exposeAllLogRecords = osEnvBean.getExposeAllLogRecordsAsMBeans();
        TypeRepository.registerTypeHandler(new ActionBeanHandler());
    }

    public void start()
    {
        JMXServer.getAgent().registerMBean(STORE_MBEAN_NAME, this);
    }

    public void stop()
    {
        unregisterMBeans();

        JMXServer.getAgent().unregisterMBean(STORE_MBEAN_NAME);

        mbeans.clear();
        types.clear();
    }

    void unregisterMBeans() {
        for (NamedOSEntryBeanMXBean mbean : mbeans.values())
            JMXServer.getAgent().unregisterMBean(mbean.getName());

        mbeans.clear();
    };

    public synchronized void probe() {
        types.checkForNewTypes(exposeAllLogRecords);

        for (Map.Entry<String, ARTypeHandler> e : TypeRepository.handlers.entrySet())
            probeHandler(e.getValue(), e.getKey());
    }

    public synchronized Set<Object> probe(String type) {
        type = TypeRepository.canonicalType(type);

        if (type.length() == 0)
            return null;

        types.checkForNewTypes(exposeAllLogRecords);

        ARTypeHandler h = types.lookupType(type);

        if (h == null)
            return null;

        probeHandler(h, type);

        Set<Object> objects = new HashSet<>();

        for (Uid uid : h.getUids())
            objects.add(mbeans.get(uid));

        return objects;
    }

    private void probeHandler(ARTypeHandler h, String type) {
        h.update(type);

        unregister(h, h.getOldUids());
        register(h, h.getNewUids());
    }

    @Override
    public void viewSubordinateAtomicActions(boolean enable) {
        //TODO
    }

    @Override
    public void setExposeAllRecordsAsMBeans(boolean exposeAllLogRecords) {
        this.exposeAllLogRecords = exposeAllLogRecords;
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        if ("remove".equals(notification.getType())) {
            // remove AbstractRecord and MBean
            Uid uid = (Uid) handback;
            NamedOSEntryBeanMXBean bean = mbeans.remove(uid);

            if (bean == null)
                return;

//            ARTypeHandler h; // TODO HERE

//            h.remove(uid);
            try {  // TODO destroy participants by doing the following in the handler
                if (!StoreManager.getRecoveryStore().remove_committed(uid, bean.getType()))
                    System.out.printf("Attempt to remove transaction failed%n");
            } catch (ObjectStoreException e) {
                System.out.printf("Attempt to remove transaction failed: %s%n", e.getMessage());
            }

            JMXServer.getAgent().unregisterMBean(bean.getName());
        }
    }

    private boolean registerMBean(Uid uid, NamedOSEntryBeanMXBean mbean) {
        if (JMXServer.getAgent().registerMBean(mbean.getName(), mbean) != null) {
            mbeans.put(uid, mbean);

            if (mbean instanceof NotificationBroadcaster)
                ((NotificationBroadcaster) mbean).addNotificationListener(this, null, uid);

            return true;
        }

        return false;
    }

    private void register(ARTypeHandler h, Set<Uid> newUids) {
        if (newUids != null && !newUids.isEmpty()) {
            for (Uid uid : newUids) {
                NamedOSEntryBeanMXBean mbean = h.createMBean(uid);
                Collection<NamedOSEntryBeanMXBean> participants = h.createRelatedMBeans(mbean);

                registerMBean(uid, mbean);

                if (participants != null) {
                    for (NamedOSEntryBeanMXBean bean : participants) {
                        if (bean instanceof Activatable)
                            ((Activatable) bean).activate();

                        registerMBean(new Uid(bean.getId()), bean);
                    }
                }
            }
        }
    }

    // TODO what is h for
    private void unregister(ARTypeHandler h, Set<Uid> oldUids) {
        if (oldUids != null && !oldUids.isEmpty()) {
            for (Uid uid : oldUids) {
                NamedOSEntryBeanMXBean mbean = mbeans.remove(uid);

                if (mbean != null)
                    JMXServer.getAgent().unregisterMBean(mbean.getName());
            }
        }
    }

    public NamedOSEntryBeanMXBean findUid(Uid uid) {
        return mbeans.get(uid);
    }

    public Set<LogRecordWrapperMXBean> findParticipants(NamedOSEntryBeanMXBean w) {
        MBeanServer mbsc = JMXServer.getAgent().getServer();
        Set<LogRecordWrapperMXBean> participants = new HashSet<>();

        for (ObjectInstance oi : findRelated(mbsc, w)) {
            LogRecordWrapperMXBean proxy = JMX.newMBeanProxy( mbsc, oi.getObjectName(), LogRecordWrapperMXBean.class, true);

            participants.add(proxy);
            //                printAtrributes(System.out, "\t\t\t", mbsc, oi);

        }

        return participants;
    }

    public Set<MBeanAccessor> findParticipantProxies(NamedOSEntryBeanMXBean w, boolean createProxy) throws MBeanAccessorException {
        MBeanServer mbsc = JMXServer.getAgent().getServer();
        Set<MBeanAccessor> participants = new HashSet<>();

        for (ObjectInstance oi : findRelated(mbsc, w)) {
            participants.add(new MBeanAccessor(oi, createProxy));
        }

        return participants;
    }
    private Set<ObjectInstance> findRelated(MBeanServer mbs, NamedOSEntryBeanMXBean w) {
        try {
            return mbs.queryMBeans(new ObjectName(w.getName() + ",puid=*"), null);
        } catch (MalformedObjectNameException e) {
            return null;
        }

/*            for (ObjectInstance oi : participants) {
                System.out.printf("\t\tParticipant: %s%n", oi);
                printAtrributes(System.out, "\t\t\t", mbs, oi);
            }
            return null;
        } catch (MalformedObjectNameException e) {
            return null;
        } catch (IntrospectionException e) {
            return null;
        } catch (ReflectionException e) {
            return null;
        } catch (InstanceNotFoundException e) {
            return null;
        }*/
    }

    void printAtrributes(PrintStream printStream, String printPrefix, MBeanServer mbs, ObjectInstance oi)
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
    }
}
