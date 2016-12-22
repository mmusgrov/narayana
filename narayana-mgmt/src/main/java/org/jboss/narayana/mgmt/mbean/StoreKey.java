package org.jboss.narayana.mgmt.mbean;

import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

public class StoreKey {
    enum StoreType {
        ActionStore(com.arjuna.ats.internal.arjuna.objectstore.ActionStore.class.getName()),
        HornetqObjectStoreAdaptor(com.arjuna.ats.internal.arjuna.objectstore.hornetq.HornetqObjectStoreAdaptor.class.getName()),
        JDBCStore(com.arjuna.ats.internal.arjuna.objectstore.jdbc.JDBCStore.class.getName()),
        FileSystemStore(com.arjuna.ats.internal.arjuna.objectstore.FileSystemStore.class.getName()),
        HashedActionStore(com.arjuna.ats.internal.arjuna.objectstore.HashedActionStore.class.getName()),
        HashedStore(com.arjuna.ats.internal.arjuna.objectstore.HashedStore.class.getName()),
        LogStore(com.arjuna.ats.internal.arjuna.objectstore.LogStore.class.getName()),
        NullActionStore(com.arjuna.ats.internal.arjuna.objectstore.NullActionStore.class.getName()),
        ShadowNoFileLockStore(com.arjuna.ats.internal.arjuna.objectstore.ShadowNoFileLockStore.class.getName()),
        TwoPhaseVolatileStore(com.arjuna.ats.internal.arjuna.objectstore.TwoPhaseVolatileStore.class.getName()),
        VolatileStore(com.arjuna.ats.internal.arjuna.objectstore.VolatileStore.class.getName()),
        CacheStore(com.arjuna.ats.internal.arjuna.objectstore.CacheStore.class.getName());

        private final String name;

        StoreType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    //FileLockingStore FileSystemStore HashedActionStore HashedStore LogStore NullActionStore ShadowNoFileLockStore TwoPhaseVolatileStore VolatileStore
    private String type;
    private String location;

    /**
     * Key for a file system based object store
     * @param type the symbolic name of the ObjectStore implementation
     * @param location path to the location of the store
     */
    public StoreKey(String type, String location) {
        this.type = (type != null) ? type :
                BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).getObjectStoreType();
        this.location = (location != null) ? location :
                BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).getObjectStoreDir();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        StoreKey storeKey = (StoreKey) o;

        if (!type.equals(storeKey.type)) return false;
        return location.equals(storeKey.location);

    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + location.hashCode();
        return result;
    }

    public String getType() {
        return type;
    }

    public String getLocation() {
        return location;
    }

    public boolean isJournal() {
        return getType().equals(StoreType.HornetqObjectStoreAdaptor.name());
    }
}
