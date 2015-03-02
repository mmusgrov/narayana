package com.arjuna.ats.internal.arjuna.tools.osb;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;

import java.util.Collection;
import java.util.Set;

public interface ARTypeHandler {
    String getType();

    void update(String typeName);

    Set<Uid> getUids();

    Set<Uid> getOldUids();

    Set<Uid> getNewUids();

    NamedOSEntryBeanMXBean createMBean(final Uid uid);

    Collection<NamedOSEntryBeanMXBean> createRelatedMBeans(final NamedOSEntryBeanMXBean bean);

    /**
     * Ask the handler to provide a handler that understands the given type
     * @param typeName the type of record to handle
     * @return a handler for typeName
     */
    ARTypeHandler getHandler(String typeName);

    void setCanonicalType(String type);
}
