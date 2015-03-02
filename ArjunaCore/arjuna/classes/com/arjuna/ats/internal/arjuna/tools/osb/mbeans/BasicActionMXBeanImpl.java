package com.arjuna.ats.internal.arjuna.tools.osb.mbeans;

import com.arjuna.ats.arjuna.common.Uid;

public class BasicActionMXBeanImpl extends NamedOSEntryBeanMBeanImpl implements BasicActionMXBean {
    public BasicActionMXBeanImpl(String type, String name, String id) {
        super(type, name, id);
    }

    public BasicActionMXBeanImpl(String typeName, String beanName, Uid uid) {
        this(typeName, beanName, uid.stringForm());
    }
}
