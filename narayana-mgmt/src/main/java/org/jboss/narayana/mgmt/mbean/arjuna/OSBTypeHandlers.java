package org.jboss.narayana.mgmt.mbean.arjuna;

import org.jboss.narayana.mgmt.internal.arjuna.OSBTypeHandler;

public class OSBTypeHandlers {
    private static OSBTypeHandler[] arjunaOsbTypes = {
            new OSBTypeHandler(
                    true,
                    true,
                    "com.arjuna.ats.arjuna.AtomicAction",
                    "org.jboss.narayana.mgmt.internal.arjuna.ActionBean",
                    "StateManager/BasicAction/TwoPhaseCoordinator/AtomicAction",
                    null
            ),
    };

    public static OSBTypeHandler[] getHandlers() {
        return arjunaOsbTypes;
    }
}
