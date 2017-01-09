package org.jboss.narayana.mgmt.mbean.jta;

import org.jboss.narayana.mgmt.internal.arjuna.OSBTypeHandler;

public class OSBTypeHandlers {
    public static final String SUBORDINATE_AA_TYPE =
            "StateManager/BasicAction/TwoPhaseCoordinator/AtomicAction/SubordinateAtomicAction/JCA";

    private static OSBTypeHandler[] jtaOsbTypes = {
            new OSBTypeHandler(
                    true,
                    true,
                    "com.arjuna.ats.internal.jta.recovery.arjunacore.RecoverConnectableAtomicAction",
                    "org.jboss.narayana.mgmt.mbean.jta.RecoverConnectableAtomicActionBean",
                    RecoverConnectableAtomicActionBean.class.getName(),
                    "StateManager/BasicAction/TwoPhaseCoordinator/AtomicActionConnectable",
                    null
            ),
            new OSBTypeHandler(
                    false,
                    true,
                    "com.arjuna.ats.internal.jta.transaction.arjunacore.subordinate.jca.SubordinateAtomicAction",
                    "org.jboss.narayana.mgmt.mbean.jta.SubordinateActionBean",
                    SubordinateActionBean.class.getName(),
                    SUBORDINATE_AA_TYPE,
                    null
            ),
    };

    public static OSBTypeHandler[] getHandlers() {
        return jtaOsbTypes;
    }
}
