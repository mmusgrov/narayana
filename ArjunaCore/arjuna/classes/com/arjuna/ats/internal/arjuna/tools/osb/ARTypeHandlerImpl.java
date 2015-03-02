package com.arjuna.ats.internal.arjuna.tools.osb;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.objectstore.ObjectStoreIterator;
import com.arjuna.ats.arjuna.objectstore.StoreManager;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMBeanImpl;

import java.util.*;

public class ARTypeHandlerImpl implements ARTypeHandler {
    private String _type;
    private Set<Uid> curr;
    private Set<Uid> prev;

    public ARTypeHandlerImpl(String _type) {
        this._type = _type;
        curr = new HashSet<>();
        prev = new HashSet<>();
    }

    public Set<Uid> getUids() {
        return curr;
    }

    public synchronized Set<Uid> getNewUids() {
        return difference(curr, prev);
    }

    public Set<Uid> getOldUids() {
        return difference(prev, curr);
    }

    private Set<Uid> difference(Set<Uid> s1, Set<Uid> s2) {
        Set<Uid> diff = new HashSet<>(s1);
        diff.removeAll(s2);

        return diff;
    }

    public String getType() {
        return _type;
    }

    public void update(String type) {
        Set<Uid> uids = new HashSet<>();
        ObjectStoreIterator iter = new ObjectStoreIterator(StoreManager.getRecoveryStore(), type);

        while (true) {
            Uid u = iter.iterate();

            if (u == null || Uid.nullUid().equals(u))
                break;

            uids.add(u);
        }

        synchronized (this) {
            prev = curr;
            curr = uids;
        }
    }

    public String getBeanName(Uid uid) {
        return getBeanName(getType(), uid);
    }

    public String getBeanName(String type, Uid uid) {
        return "jboss.jta:type=ObjectStore,itype=" + type + ",uid=" + uid.fileStringForm();
    }

    @Override
    public Collection<NamedOSEntryBeanMXBean> createRelatedMBeans(NamedOSEntryBeanMXBean bean) {
        ARTypeHandler th = TypeRepository.lookupType(getType());

        return th == null ? null : th.createRelatedMBeans(bean);
    }

    @Override
    public ARTypeHandler getHandler(String typeName) {
        return new ARTypeHandlerImpl(typeName);
    }

    @Override
    public void setCanonicalType(String type) {
        _type = type;
    }

    NamedOSEntryBeanMXBean createMBean(final String type, final String name, final Uid uid) {
        return new NamedOSEntryBeanMBeanImpl(type, name, uid.stringForm());
/*        return new NamedOSEntryBeanMBean() {

            @Override
            public String getType() {
                return type;
            }

            @Override
            public String getId() {
                return uid.stringForm();
            }

            @Override
            public String remove() {
                return null;
            }

            @Override
            public String getName() { return name; }
        };
        */
    }

    public NamedOSEntryBeanMXBean createMBean(final Uid uid) {
        return createMBean(_type, getBeanName(uid), uid);
    }
}
