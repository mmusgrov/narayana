package io.narayana.lra.coordinator.domain.model;

import com.arjuna.ats.arjuna.common.Uid;

class LRAUid extends Uid {
    private LRALock lockUid;

    LRAUid() {
        this.lockUid = new LRALock();
    }

    LRALock getLockUid() {
        return lockUid;
    }
}
