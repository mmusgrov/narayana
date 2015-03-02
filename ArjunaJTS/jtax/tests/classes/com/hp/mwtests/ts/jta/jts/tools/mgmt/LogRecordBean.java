package com.hp.mwtests.ts.jta.jts.tools.mgmt;

import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.arjuna.coordinator.HeuristicInformation;
import com.arjuna.ats.arjuna.tools.osb.mbean.HeuristicStatus;
import com.arjuna.ats.arjuna.tools.osb.mbean.LogRecordWrapperMXBean;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMBeanImpl;

public class LogRecordBean implements LogRecordWrapperMXBean {
   // public class LogRecordBean extends NamedOSEntryBeanMBeanImpl implements NamedOSEntryBeanMXBean, LogRecordWrapperMXBean {
    private BasicActionMXBean parent;
    private AbstractRecord ar;
    private ParticipantStatus listType;
    private NamedOSEntryBeanMBeanImpl impl;

    public LogRecordBean(String type, String name, String id) {
        //super(type, name, id);
        impl = new NamedOSEntryBeanMBeanImpl(type, name, id);
    }

    public LogRecordBean(BasicActionMXBean baa, AbstractRecord ar, ParticipantStatus listType) {
        this(ar.type(), baa.getName() + ",puid=" + ar.order().fileStringForm(), ar.order().stringForm());
        this.parent = baa;
        this.ar = ar;
        this.listType = listType;
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
        if (getListType().equals(newState))
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
        }

        // if this record has a parent ask it to update the status
//      return "TODO";
        if (parent != null && parent.setStatus(this, newState)) {
            listType = newState;

            if (newState.equals(ParticipantStatus.PREPARED))
                return "participant recovery will be attempted during the next recovery pass";

            return "participant status change was successful";
        } else {
            // TODO what about records without a parent eg CosTransaction records
            return "participant status change failed";
        }
    }

    public String getType() {
        return ar == null ? "uninitialised" : ar.type();
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

/*    public boolean activate() {
        if (!activated && ar != null)
            try {
                activated = ar.activate();
            } catch (Exception e) {
                activated = false;
                tsLogger.logger.warn("Activate of " + rec + " failed: " + e.getMessage());
            }

        return activated;
    }*/

/*    public StringBuilder toString(String prefix, StringBuilder sb) {
        prefix += "\t";
        if (parent != null && ar != null) {
            sb.append('\n').append(prefix).append(parent.getUid(ar));
            sb.append('\n').append(prefix).append(listType.toString());
            sb.append('\n').append(prefix).append(ar.type());
            sb.append('\n').append(prefix).append(parent.getCreationTime());
            sb.append('\n').append(prefix).append(parent.getAgeInSeconds());
        } else {
            sb.append('\n').append(prefix).append(_uidWrapper.getName());
        }

        return sb;
    }*/

    public String callMethod(Object object, String mName)
    {
        try {
            return (String) object.getClass().getMethod(mName).invoke(object);
        } catch (NoSuchMethodException e) {
            return "Not supported";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
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
