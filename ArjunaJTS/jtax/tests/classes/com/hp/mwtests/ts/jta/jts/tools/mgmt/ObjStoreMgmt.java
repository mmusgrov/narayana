package com.hp.mwtests.ts.jta.jts.tools.mgmt;

import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.exceptions.ObjectStoreException;
import com.arjuna.ats.arjuna.objectstore.ObjectStoreIterator;
import com.arjuna.ats.arjuna.objectstore.RecoveryStore;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.arjuna.tools.osb.mbean.ObjStoreBrowserMXBean;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationListener;
import java.util.*;

public class ObjStoreMgmt implements ObjStoreBrowserMXBean, NotificationListener {

    private TypeRepository handlers;
    private boolean exposeAllLogRecords;
    private RecoveryStore store;
    private Map<Uid, NamedOSEntryBeanMXBean> mbeans ;

    public void start()
    {
        JMXServer.getAgent().registerMBean(JMXServer.STORE_MBEAN_NAME, this);
    }

    public ObjStoreMgmt() {
        ObjectStoreEnvironmentBean osEnvBean = BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class);
        store = StoreManager.getRecoveryStore();
        handlers = new TypeRepository(store);
        exposeAllLogRecords = osEnvBean.getExposeAllLogRecordsAsMBeans();
        mbeans = new HashMap<>();
    }

    @Override
    public void viewSubordinateAtomicActions(boolean enable) {
        ; //TODO
    }

    @Override
    public void setExposeAllRecordsAsMBeans(boolean exposeAllLogRecords) {
        this.exposeAllLogRecords = exposeAllLogRecords;
    }

    private void update(String type) {
        Set<Uid> uids = new HashSet<>();
        ObjectStoreIterator iter = new ObjectStoreIterator(store, type);

        while (true) {
            Uid u = iter.iterate();

            if (u == null || Uid.nullUid().equals(u))
                break;

            uids.add(u);
        }

        synchronized (this) {
//            prev = curr;
//            curr = uids;
        }
    }

    @Override
    public synchronized void probe() {
        handlers.checkForNewTypes(exposeAllLogRecords);
        Set<Uid> removedUids = new HashSet<>(mbeans.keySet());

        for (String type : handlers.getTypes()) {
            ARHandler th = handlers.lookupType(type);

            ObjectStoreIterator iter = new ObjectStoreIterator(store, type);

            while (true) {
                Uid u = iter.iterate();

                if (u == null || Uid.nullUid().equals(u))
                    break;

                try {
                    InputObjectState ios = store.read_committed(u, type);

                    NamedOSEntryBeanMXBean bean = th.createBean(u, ios, type);

                    if (bean != null) {
                        registerMBean(removedUids, getUid(bean), bean);

                        Collection<NamedOSEntryBeanMXBean> participants = new ArrayList<>();

                        th.createRelatedMBeans(participants, bean);

                        for (NamedOSEntryBeanMXBean pbean : participants)
                            registerMBean(removedUids, getUid(pbean), pbean);
                    }
                } catch (ObjectStoreException e) {
                    e.printStackTrace();
                }
            };
        }

        // anything left in registeredUids needs unregistering
        for (Uid uid : removedUids) {
            unregisterMBean(uid);
        }
    }

    private Uid getUid(NamedOSEntryBeanMXBean bean) {
        return new Uid(bean.getId());
    }

    public void clear() {
        handlers.clear();
    }

    public void stop() {
        JMXServer server = JMXServer.getAgent();

        for (NamedOSEntryBeanMXBean bean : mbeans.values())
            server.unregisterMBean(bean.getName());

        server.unregisterMBean(JMXServer.STORE_MBEAN_NAME);

        clear();
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

    private boolean registerMBean(Set<Uid> removedUids, Uid uid, NamedOSEntryBeanMXBean mbean) {
        if (JMXServer.getAgent().registerMBean(mbean.getName(), mbean) != null) {
            mbeans.put(uid, mbean);

            removedUids.remove(uid);

            if (mbean instanceof NotificationBroadcaster)
                ((NotificationBroadcaster) mbean).addNotificationListener(this, null, uid);

            return true;
        }

        return false;
    }

    private boolean unregisterMBean(Uid uid) {
        NamedOSEntryBeanMXBean mbean = mbeans.remove(uid);

        if (mbean !=  null) {
            if (mbean instanceof NotificationBroadcaster)
                try {
                    ((NotificationBroadcaster) mbean).removeNotificationListener(this);
                } catch (ListenerNotFoundException e) {
                    System.err.printf("removeNotificationListener failed for MBean %s%n", mbean.getName());
                }

            if (JMXServer.getAgent().unregisterMBean(mbean.getName())) {
                System.err.printf("unregisterMBean failed for MBean %s%n", mbean.getName());

                return false;
            }
        }

        return true;
    }
}
