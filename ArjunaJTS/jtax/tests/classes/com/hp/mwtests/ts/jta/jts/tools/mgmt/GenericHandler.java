package com.hp.mwtests.ts.jta.jts.tools.mgmt;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;

import java.util.Collection;

public class GenericHandler implements ARHandler {
    private String type;
    private String canonicalType;

    public GenericHandler(String type) {
        this.type = type;
        canonicalType = JMXServer.canonicalType(type);
    }

    @Override
    public ARHandler getHandler(String typeName) {
        return this;
    }

    @Override
    public String getType() {
        return canonicalType;
    }

    @Override
    public NamedOSEntryBeanMXBean createBean(Uid uid, InputObjectState ios, final String type) {
         return new GenericARMXBean(type, uid.fileStringForm());
    }

    @Override
    public void createRelatedMBeans(Collection<NamedOSEntryBeanMXBean> beans, NamedOSEntryBeanMXBean bean) {
    }
}
