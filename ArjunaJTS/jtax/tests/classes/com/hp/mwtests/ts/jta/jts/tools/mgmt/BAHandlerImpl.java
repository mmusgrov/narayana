package com.hp.mwtests.ts.jta.jts.tools.mgmt;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.coordinator.BasicAction;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;

import java.util.Collection;

public class BAHandlerImpl implements ARHandler {
    private String type;
    private String canonicalType;
    BasicActionMXBean bean;

    public BAHandlerImpl(String typeName) {
        this.type = typeName;
        canonicalType = ObjStoreMBeanON.canonicalType(typeName);
    }

    public BAHandlerImpl() {
        this(new BasicAction().type());
    }

    @Override
    public ARHandler getHandler(String typeName) {
        if (canonicalType.startsWith(typeName) || typeName.startsWith(canonicalType))
            return this;

        return null;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public NamedOSEntryBeanMXBean createBean(Uid uid, InputObjectState os, String type) {
        BasicActionMXBean bean = new BasicActionMXBean(type, uid.fileStringForm());

        bean.updateState(os, canonicalType, getHeaderStateReader(type));

        return bean;
    }

    //   private boolean restoreRecord()
    @Override
    public void createRelatedMBeans(TypeRepository typeHandlers, Collection<NamedOSEntryBeanMXBean> beans, NamedOSEntryBeanMXBean bean) {
        if (bean instanceof BasicActionMXBean) {
            BasicActionMXBean mxBean = (BasicActionMXBean) bean;

            mxBean.getParticipants(typeHandlers, beans, ParticipantStatus.PREPARED);
            mxBean.getParticipants(typeHandlers, beans, ParticipantStatus.HEURISTIC);
        }
    }

    public HeaderStateReader getHeaderStateReader(String typeName) {
        return new HeaderStateReader();
    }
}
