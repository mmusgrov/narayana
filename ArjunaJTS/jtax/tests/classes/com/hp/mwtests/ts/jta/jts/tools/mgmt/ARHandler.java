package com.hp.mwtests.ts.jta.jts.tools.mgmt;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.internal.arjuna.tools.osb.ARTypeHandler;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;

import java.util.Collection;

public interface ARHandler {

    ARHandler getHandler(String typeName);

    String getType();

    NamedOSEntryBeanMXBean createBean(Uid uid, InputObjectState ios, String type);

    void createRelatedMBeans(TypeRepository typeHandlers, Collection<NamedOSEntryBeanMXBean> beans, NamedOSEntryBeanMXBean bean);
}
