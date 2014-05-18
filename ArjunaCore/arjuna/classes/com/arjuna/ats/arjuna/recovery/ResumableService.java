package com.arjuna.ats.arjuna.recovery;

public interface ResumableService {
    void resumeService();
    void suspendService();
    boolean isSuspended();
}
