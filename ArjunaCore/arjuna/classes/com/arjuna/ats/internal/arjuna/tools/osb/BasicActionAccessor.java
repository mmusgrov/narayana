package com.arjuna.ats.internal.arjuna.tools.osb;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.coordinator.*;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.LogRecordBean;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.ParticipantStatus;

public class BasicActionAccessor extends BasicAction {
    private String type;

    public BasicActionAccessor(String type, Uid objUid) {
        super(objUid);
        this.type = type;
    }

    RecordList getList(ParticipantStatus type) {
        switch (type) {
            case PENDING: return pendingList;
            case PREPARED: return preparedList;
            case READONLY: return readonlyList;
            case FAILED: return failedList;
            case HEURISTIC: return heuristicList;
            default: return null;
        }
    }

    @Override
    public String type () {
        return type;
    }
    public void remove(AbstractRecord ar, ParticipantStatus listType) {
        // TODO these need to be remote MBean ops
        RecordList rl = getList(listType);

        if (rl != null && rl.size() > 0) {
            if (rl.remove(ar)) {
                updateState(); // rewrite the list
            }
        }
    }

    public boolean setStatus(LogRecordBean logRecordBean, ParticipantStatus newState) {
        ParticipantStatus lt = logRecordBean.getListType();
        AbstractRecord targRecord = logRecordBean.getRecord();

        RecordList oldList = getList(lt);
        RecordList newList = getList(newState);

        // move the record from currList to targList
        if (oldList.remove(targRecord)) {

            if (newList.insert(targRecord)) {
                if (lt.equals(ParticipantStatus.HEURISTIC)) {
                    switch (newState) {
                        case FAILED:
                            clearHeuristicDecision(TwoPhaseOutcome.FINISH_ERROR);
                            break;
                        case PENDING:
                            clearHeuristicDecision(TwoPhaseOutcome.NOT_PREPARED);
                            break;
                        case PREPARED:
                            clearHeuristicDecision(TwoPhaseOutcome.PREPARE_OK);
                            break;
                        case READONLY:
                            clearHeuristicDecision(TwoPhaseOutcome.PREPARE_READONLY);
                            break;
                        default:
                            break;
                    }
                }

                updateState();

                return true;
            }
        }

        return false;
    }

    public void clearHeuristicDecision(int newDecision) {
        if (super.heuristicList.size() == 0)
            setHeuristicDecision(newDecision);
    }

    //activate() {
    //ObjectStoreEnvironmentBean storeEnvBean = BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "communicationStore");
    // ObjectStoreEnvironmentBean store = BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class);
//        RecoveryStore store = StoreManager.getRecoveryStore();
//        InputObjectState ios = store.read_committed(uid, getType());
//        ba.restore_state(ios, ObjectStatus.UNKNOWN_STATUS);

}
