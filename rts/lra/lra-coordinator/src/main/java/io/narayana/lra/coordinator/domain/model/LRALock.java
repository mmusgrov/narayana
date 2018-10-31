package io.narayana.lra.coordinator.domain.model;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.ObjectModel;
import com.arjuna.ats.arjuna.ObjectType;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.arjuna.state.OutputObjectState;
import com.arjuna.ats.txoj.Lock;
import com.arjuna.ats.txoj.LockManager;
import com.arjuna.ats.txoj.LockResult;

import java.io.IOException;

public class LRALock extends LockManager {
    private static final String LRA_LOCK_TYPE = "/StateManager/LockManager/LRA";
    private String lraId;

    public LRALock() {
        super(ObjectType.ANDPERSISTENT, ObjectModel.MULTIPLE);
    }

    public LRALock(Uid uid, String lraId) {
        super(uid, ObjectType.ANDPERSISTENT, ObjectModel.MULTIPLE);
        this.lraId = lraId;
    }

    public static String getType() {
        return LRA_LOCK_TYPE;
    }

    @Override
    public synchronized boolean save_state(OutputObjectState os, int ot) {
        if (super.save_state(os, ot)) {
            try {
                os.packString(lraId);
            } catch (IOException e) {
                return false;
            }
        }

        return true;
    }

    @Override
    public synchronized boolean restore_state(InputObjectState os, int ot) {
        if (super.restore_state(os, ot)) {
            try {
                lraId = os.unpackString();
            } catch (IOException e) {
                return false;
            }
        }

        return true;
    }

    public String type() {
        return LRA_LOCK_TYPE;
    }

    boolean doProtected(int lockMode, Action action) {
        AtomicAction aa = new AtomicAction();

        try {
            aa.begin(1); // 1 second

            if (setlock(new Lock(lockMode), 0) == LockResult.GRANTED) {
                return action.doIt();
            }
        } finally {
            aa.commit();
        }

        return false;
    }

    public Uid getLraId() {
        return new Uid(lraId);
    }

    void setLraId(Uid uid) {
        lraId = uid.stringForm();
    }
}
