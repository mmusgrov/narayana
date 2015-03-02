package com.arjuna.ats.jta.tools.mbeans;

import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.internal.arjuna.tools.osb.ActionBeanHandler;
import com.arjuna.ats.internal.arjuna.tools.osb.BasicActionAccessor;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.ParticipantStatus;

// TODO move these into an internal package
public class JTAActionBeanHandler extends ActionBeanHandler {
    public JTAActionBeanHandler() {
        super();
    }

    @Override
    protected NamedOSEntryBeanMXBean createMBean(String beanName, BasicActionAccessor aa, AbstractRecord rec, ParticipantStatus arType) {
        if (rec instanceof com.arjuna.ats.internal.jta.resources.arjunacore.XAResourceRecord)
            return new XAResourceRecordBean(beanName, aa, rec, arType);
//        else if (rec instanceof com.arjuna.ats.internal.jta.resources.arjunacore.CommitMarkableResourceRecord)
//            return new com.arjuna.ats.internal.jta.tools.osb.mbean.jta.CommitMarkableResourceRecordBean(null, rec, arType);
        else
            return super.createMBean(beanName, aa, rec, arType);
    }
}
