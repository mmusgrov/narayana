package com.arjuna.ats.internal.arjuna.tools.osb;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.arjuna.coordinator.RecordList;
import com.arjuna.ats.arjuna.tools.osb.mbean.OSEntryBeanMXBean;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.BasicActionMXBeanImpl;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.LogRecordBean;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.ParticipantStatus;

import java.util.*;

public class ActionBeanHandler extends ARTypeHandlerImpl { //OSEntryBean implements ActionBeanMBean, ARTypeHandler {
    private static final String TYPE_NAME = "StateManager/BasicAction/TwoPhaseCoordinator/AtomicAction";
    private static final String AR_CLASS = "com.arjuna.ats.arjuna.AtomicAction";

    public ActionBeanHandler() {
        super(TYPE_NAME);
    }

    public ActionBeanHandler(String typeName) {
        super(typeName);
    }

    @Override
    public void update(String typeName) {
        // assert typeName == TYPE_NAME
        super.update(typeName);
    }

    @Override
    public Collection<NamedOSEntryBeanMXBean> createRelatedMBeans(final NamedOSEntryBeanMXBean bean) {

        BasicActionAccessor aa = new BasicActionAccessor(getType(), new Uid(bean.getId()));

        if (!aa.activate())
            return null;

        String beanName = bean.getName();
        Collection<NamedOSEntryBeanMXBean> beans = new ArrayList<>();

        // create beans representing each participant
        for (ParticipantStatus arType : ParticipantStatus.values()) {
            RecordList list = aa.getList(arType);

            if (list != null) {
                for (AbstractRecord rec = list.peekFront(); rec != null; rec = list.peekNext(rec)) {
                    ARTypeHandler h = TypeRepository.lookupType(rec.type());

                    if (h == null)
                        beans.add(createMBean(beanName, aa, rec, arType));
                    else
                        ;//TODO beans.add(h.createMBea
                }
            }
        }

        return beans;
    }

    protected NamedOSEntryBeanMXBean createMBean(String beanName, BasicActionAccessor aa, AbstractRecord rec, ParticipantStatus arType) {
        return new LogRecordBean(beanName, aa, rec, arType);
    }

    @Override
    public ARTypeHandler getHandler(String typeName) {
        return null;
    }

    @Override
    public NamedOSEntryBeanMXBean createMBean(final Uid uid) {
        return new BasicActionMXBeanImpl(getType(), getBeanName(getType(), uid), uid);
    }
}
