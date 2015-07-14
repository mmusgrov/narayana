package com.hp.mwtests.ts.jta.jts.tools.mgmt;

import com.arjuna.ats.arjuna.ObjectType;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.coordinator.*;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.internal.arjuna.tools.log.EditableAtomicAction;
import com.arjuna.ats.internal.arjuna.tools.log.EditableTransaction;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;

import javax.management.InstanceNotFoundException;
import java.io.IOException;
import java.util.Collection;

public class BasicActionMXBean implements NamedOSEntryBeanMXBean {

    private String type;
    private String id;

    private String name;
    private HeaderState headerState;
    private int actionStatus; // ActionStatus
    private int actionType; // ActionType
    private int heuristicDecision; // TwoPhaseOutcome.HEURISTIC_ROLLBACK etc
    private int record_type;
    private RecordList preparedList;
    private RecordList heuristicList;
    private Collection<NamedOSEntryBeanMXBean> participants;
    private boolean restored;

    public BasicActionMXBean(String type, String id) {
        this.type = type;
        this.id = id;
        name = ObjStoreMBeanON.generateObjectName(type, id);
        record_type = RecordType.NONE_RECORD;
        actionStatus = ActionStatus.INVALID;
        actionType = ActionType.TOP_LEVEL;
        heuristicDecision = TwoPhaseOutcome.PREPARE_OK;

        preparedList = new RecordList();
        heuristicList = new RecordList();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return null;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String remove() {
        return null;
    }

    public int getHeuristicDecision() {
        return heuristicDecision;
    }

    public int getActionType() {
        return actionType;
    }

    public int getActionStatus() {
        return actionStatus;
    }

    boolean updateState(InputObjectState os, String type, HeaderStateReader  headerStateReader) {
        try {
            AbstractRecord record;

            headerState = headerStateReader.unpackHeader(os);
            // TODO expose headerState
            os.unpackBoolean(); // read pastFirstParticipant

            restored = true;

            while ((record = unpackRecord(os)) != null)
                if (!restoreRecord(record, os, preparedList))
                    restored = false;

            if (os.unpackInt() != 0)
                while ((record = unpackRecord(os)) != null)
                    if (!restoreRecord(record, os, heuristicList))
                        restored = false;

            actionStatus = os.unpackInt(); // ActionStatus
            actionType = os.unpackInt(); // ActionType
            heuristicDecision = os.unpackInt(); // TwoPhaseOutcome.HEURISTIC_ROLLBACK etc
        } catch (IOException e) {
            System.err.printf("restore basic action %s failed with error %s%n", toString(), e.getMessage());
            restored = false;
        }

        return restored;
    }

    private AbstractRecord unpackRecord(InputObjectState os) throws IOException {
        record_type = os.unpackInt();

        if (record_type != RecordType.NONE_RECORD)
            return AbstractRecord.create(record_type);

        return null;
    }

    private boolean restoreRecord(AbstractRecord record, InputObjectState os, RecordList list) {
        boolean res = (record.restore_state(os, ObjectType.ANDPERSISTENT) && list.insert(record));

        if (!res) {
            System.err.printf("restore record type %s failed%n", record.toString());
        }

        return res;
    }

    private void createBeans(TypeRepository typeHandlers, Collection<NamedOSEntryBeanMXBean> participants, RecordList list, ParticipantStatus listType) {
        for (AbstractRecord rec = list.peekFront(); rec != null; rec = list.peekNext(rec)) {
            ARPHandler handler = typeHandlers.lookupPType(rec.type());
            LogRecordBean pBean = handler.createParticipantBean(this, rec, listType);

            if (pBean == null)
                pBean = new LogRecordBean(this, rec, listType);

            participants.add(pBean);
        }
    }

    public void getParticipants(TypeRepository typeHandlers, Collection<NamedOSEntryBeanMXBean> participants, ParticipantStatus participantStatus) {
        if (participantStatus.equals(ParticipantStatus.PREPARED))
            createBeans(typeHandlers, participants, preparedList, ParticipantStatus.PREPARED);
        else if (participantStatus.equals(ParticipantStatus.HEURISTIC))
            createBeans(typeHandlers, participants, heuristicList, ParticipantStatus.HEURISTIC);
    }

    public Pair<Integer, AbstractRecord> getHeuristicRecord(Uid uid) {
        RecordListIterator iter = new RecordListIterator(heuristicList);
        AbstractRecord rec;
        int i = 0;

        while (((rec = iter.iterate()) != null)) {
            if (rec.order().equals(uid))
                return new Pair<>(i, rec);

            i += 1;
        }

        return null;
    }

    // similar to EditableTransaction#moveHeuristicToPrepared
    public void moveHeuristicToPrepared(Uid actionUid, Uid heuristicUid) throws InstanceNotFoundException, IndexOutOfBoundsException {
        Pair<Integer, AbstractRecord> item = getHeuristicRecord(heuristicUid);

        if (item == null)
            throw new InstanceNotFoundException(heuristicUid.fileStringForm());

        EditableTransaction et = new EditableAtomicAction(actionUid);

        et.moveHeuristicToPrepared(item.getKey());

        heuristicList.remove(item.getVal());
        preparedList.insert(item.getVal());
    }

    // similar to EditableTransaction#deleteHeuristicParticipant
    public void deleteHeuristicParticipant(Uid uid) throws InstanceNotFoundException, IndexOutOfBoundsException {
        Pair<Integer, AbstractRecord> item = getHeuristicRecord(uid);

        if (item == null)
            throw new InstanceNotFoundException(uid.fileStringForm());

        EditableTransaction et = new EditableAtomicAction(uid);;

        et.deleteHeuristicParticipant(item.getKey());
        heuristicList.remove(item.getVal());
    }

    static private class Pair<K, V> {
        K key;
        V val;

        public Pair(K key, V val) {
            this.key = key;
            this.val = val;
        }

        public K getKey() {
            return key;
        }

        public V getVal() {
            return val;
        }
    }
}
