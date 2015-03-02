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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ObjStoreMgmt implements ObjStoreBrowserMXBean {

    private TypeRepository handlers;
    private boolean exposeAllLogRecords;
    private RecoveryStore store;

    public void start()
    {
        JMXServer.getAgent().registerMBean(JMXServer.STORE_MBEAN_NAME, this);
    }

    public ObjStoreMgmt() {
        ObjectStoreEnvironmentBean osEnvBean = BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class);
        store = StoreManager.getRecoveryStore();
        handlers = new TypeRepository(store);
        exposeAllLogRecords = osEnvBean.getExposeAllLogRecordsAsMBeans();
    }

    @Override
    public void viewSubordinateAtomicActions(boolean enable) {
        this.exposeAllLogRecords = enable;
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
    public void probe() {
        handlers.checkForNewTypes(exposeAllLogRecords);

        for (String type : handlers.getTypes()) {
            ARHandler th = handlers.lookupType(type);

            Set<Uid> uids = new HashSet<>();
            ObjectStoreIterator iter = new ObjectStoreIterator(store, type);

            while (true) {
                Uid u = iter.iterate();

                if (u == null || Uid.nullUid().equals(u))
                    break;

                uids.add(u);

                try {
                    InputObjectState ios = store.read_committed(u, type);

                    NamedOSEntryBeanMXBean bean = th.createBean(u, ios, type);

                    if (bean != null) {
                        JMXServer.getAgent().registerMBean(bean.getName(), bean);

                        Collection<NamedOSEntryBeanMXBean> participants = new ArrayList<>();

                        th.createRelatedMBeans(participants, bean);

                        if (participants != null) {
                            for (NamedOSEntryBeanMXBean pbean : participants) {
                                JMXServer.getAgent().registerMBean(pbean.getName(), pbean);
                            }
                        }
                    }
                } catch (ObjectStoreException e) {
                    e.printStackTrace();
                }
            };
        }
    }

    public void clear() {
        handlers.clear();
    }

}
