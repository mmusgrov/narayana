package com.hp.mwtests.ts.jta.jts.tools.mgmt;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.arjuna.coordinator.HeuristicInformation;
import com.arjuna.ats.arjuna.tools.osb.mbean.HeuristicStatus;
import com.arjuna.ats.arjuna.tools.osb.mbean.LogRecordWrapperMXBean;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMBeanImpl;

import javax.management.InstanceNotFoundException;

public class LogRecordBean implements LogRecordWrapperMXBean {
    // public class LogRecordBean extends NamedOSEntryBeanMBeanImpl implements NamedOSEntryBeanMXBean, LogRecordWrapperMXBean {
    private BasicActionMXBean parent;
    private AbstractRecord ar;
    private ParticipantStatus listType;
    private NamedOSEntryBeanMBeanImpl impl;

    public LogRecordBean(String type, String name, String id) {
        impl = new NamedOSEntryBeanMBeanImpl(type, name, id);
    }

    public LogRecordBean(BasicActionMXBean baa, AbstractRecord ar, ParticipantStatus listType) {
        this(ar.type(), getON(baa, ar), ar.order().fileStringForm());

        this.parent = baa;
        this.ar = ar;
        this.listType = listType;
    }

    private static String getON(BasicActionMXBean baa, AbstractRecord ar) {
        String type = ar.type();
        Uid pUid = new Uid(baa.getId());

        return ObjStoreMBeanON.generateParticipantObjectName(type, pUid, ar.order());
    }

    @Override
    public String getStatus() {
        if (isHeuristic()) {
            String type = getHeuristicStatus();

            if (!type.equals(HeuristicStatus.UNKNOWN.name()))
                return type;
        }

        return listType.toString();
    }

    @Override
    public void setStatus(String newState) {
        doSetStatus(newState);
    }

    @Override
    public String clearHeuristic() {
        return doSetStatus("PREPARED");
    }

    public String doSetStatus(String newState) {
        try {
            return setStatus(Enum.valueOf(ParticipantStatus.class, newState.toUpperCase()));
        } catch (IllegalArgumentException e) {
            StringBuilder sb = new StringBuilder("Valid status values are: ");

            for (ParticipantStatus lt : ParticipantStatus.values()) {
                sb.append(lt.name()).append(", ");
            }

            sb.append(" and only HEURISTIC and PREPARED will persist after JVM restart.");

            return sb.toString();
        }
    }

    public String setStatus(ParticipantStatus newState) {
        if (getListType().equals(newState) && newState.equals(ParticipantStatus.PREPARED))
            return "participant is prepared for recovery";

		/*
		 * Only move a heuristic to the prepared list if it hasn't already committed or rolled back
		 */
        if (newState.equals(ParticipantStatus.PREPARED) && getListType().equals(ParticipantStatus.HEURISTIC)) {
            HeuristicStatus heuristicStatus = HeuristicStatus.valueOf(getHeuristicStatus());

            if (heuristicStatus.equals(HeuristicStatus.HEURISTIC_COMMIT) ||
                    heuristicStatus.equals(HeuristicStatus.HEURISTIC_ROLLBACK)) {
                return "participant has already committed or rolled back";
            }

            if (parent != null) {
                try {
                    parent.moveHeuristicToPrepared(new Uid(parent.getId()), new Uid(getId()));
                    listType = newState;
                    return "participant recovery will be attempted during the next recovery pass";
                } catch (InstanceNotFoundException |IndexOutOfBoundsException e) {
                    return "participant status change failed";
                }
            }
        }

        listType = newState;

        if (newState.equals(ParticipantStatus.PREPARED))
            return "participant recovery will be attempted during the next recovery pass";

        return "participant status change was successful";
    }

    public String getType() {
        return impl.getType();
    }

    @Override
    public String getId() {
        return impl.getId();
    }

    @Override
    public boolean isParticipant() {
        return true;
    }

    public AbstractRecord getRecord() {
        return ar;
    }

    public ParticipantStatus getListType() {
        return listType;
    }

    public boolean isHeuristic() {
        return listType.equals(ParticipantStatus.HEURISTIC);
    }

    @Override
    public String getHeuristicStatus() {
        Object heuristicInformation = ar.value();
        HeuristicStatus hs;

        if (heuristicInformation  != null && heuristicInformation instanceof HeuristicInformation) {
            HeuristicInformation hi = (HeuristicInformation) heuristicInformation;
            hs = HeuristicStatus.intToStatus(hi.getHeuristicType());
        } else {
            hs = HeuristicStatus.UNKNOWN;
        }

        return hs.name();
    }

    @Override
    public String remove() {
        return remove(true);
    }

    private String remove(boolean reprobe) { // TODO why reprobe
        return impl.remove();
    }

    @Override
    public String getName() {
        return impl.getName();
    }
}
