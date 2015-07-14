package com.hp.mwtests.ts.jta.jts.tools.mgmt;

import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.BasicActionMXBean;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.LogRecordBean;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.ParticipantStatus;

public interface ARPHandler {
    String getType();
    LogRecordBean createParticipantBean(BasicActionMXBean baa, AbstractRecord ar, ParticipantStatus listType);
}
