package com.arjuna.ats.internal.jta.tools.osb.mbean.jts.osb;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.internal.arjuna.tools.osb.ARTypeHandler;
import com.arjuna.ats.internal.arjuna.tools.osb.ActionBeanHandler;
import com.arjuna.ats.internal.arjuna.tools.osb.BasicActionAccessor;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.LogRecordBean;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.ParticipantStatus;
import com.arjuna.ats.internal.jts.orbspecific.coordinator.ArjunaTransactionImple;

import java.util.Collection;
import java.util.Set;

public class JTSARHandler extends ActionBeanHandler {
    public JTSARHandler() {
        super(ArjunaTransactionImple.typeName());
    }

    @Override
    public void update(String typeName) {
        super.update(typeName);
    }

    @Override
    public Set<Uid> getUids() {
        return null;
    }

    @Override
    public Set<Uid> getOldUids() {
        return super.getOldUids();
    }

    @Override
    public Set<Uid> getNewUids() {
        return super.getNewUids();
    }

    @Override
    public NamedOSEntryBeanMXBean createMBean(Uid uid) {
        return super.createMBean(uid);
    }

    @Override
    public Collection<NamedOSEntryBeanMXBean> createRelatedMBeans(NamedOSEntryBeanMXBean bean) {
        return super.createRelatedMBeans(bean);
    }

    @Override
    public ARTypeHandler getHandler(String typeName) {
        return super.getHandler(typeName);
    }

    protected NamedOSEntryBeanMXBean createMBean(String beanName, BasicActionAccessor aa, AbstractRecord rec, ParticipantStatus arType) {
        // TODO check for XAR types
        return new LogRecordBean(beanName, aa, rec, arType);
    }
}
