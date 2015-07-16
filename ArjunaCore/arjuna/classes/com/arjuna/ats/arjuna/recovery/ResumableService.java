package com.arjuna.ats.arjuna.recovery;

public interface ResumableService {
    void resumeService();
    ResumableService suspendService();
    boolean isSuspended();
}
