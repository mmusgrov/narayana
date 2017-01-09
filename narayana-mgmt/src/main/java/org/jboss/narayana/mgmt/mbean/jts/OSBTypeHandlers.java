package org.jboss.narayana.mgmt.mbean.jts;

import org.jboss.narayana.mgmt.internal.arjuna.OSBTypeHandler;

public class OSBTypeHandlers {
    public static final String SUBORDINATE_ATI_TYPE =
                    "StateManager/BasicAction/TwoPhaseCoordinator/ArjunaTransactionImple/ServerTransaction/JCA";
    private static OSBTypeHandler[] jtsOsbTypes = {
            new OSBTypeHandler(
                    true,
                    false, // by default do not probe for this type
                    "org.jboss.narayana.mgmt.internal.jts.JTSXAResourceRecordWrapper",
                    "org.jboss.narayana.mgmt.internal.jts.JTSXAResourceRecordWrapper",
                    "CosTransactions/XAResourceRecord",
                    null
            ),
/* this is a test only class (see NewTypeTest.java)*/
           new OSBTypeHandler(
                    false,
                    true,
                    "org.jboss.narayana.mgmt.internal.jts.JCAServerTransactionWrapper",
                    "org.jboss.narayana.mgmt.internal.jts.JTSActionBean",
                    SUBORDINATE_ATI_TYPE,
                    "org.jboss.narayana.mgmt.mbean.jts.JCAServerTransactionHeaderReader"
            ),
            new OSBTypeHandler(
                    true,
                    true,
                    "org.jboss.narayana.mgmt.internal.jts.ArjunaTransactionImpleWrapper",
                    "org.jboss.narayana.mgmt.internal.jts.JTSActionBean",
                    "StateManager/BasicAction/TwoPhaseCoordinator/ArjunaTransactionImple",
                    null
            ),
            new OSBTypeHandler(
                    true,
                    true,
                    "org.jboss.narayana.mgmt.internal.jts.ServerTransactionWrapper",
                    "org.jboss.narayana.mgmt.internal.jts.JTSActionBean",
                    "StateManager/BasicAction/TwoPhaseCoordinator/ArjunaTransactionImple/ServerTransaction",
                    "org.jboss.narayana.mgmt.internal.jts.ServerTransactionHeaderReader"
            ),
            new OSBTypeHandler(
                    true,
                    true,
                    "org.jboss.narayana.mgmt.internal.jts.ServerTransactionWrapper",
                    "org.jboss.narayana.mgmt.internal.jts.JTSActionBean",
                    "StateManager/BasicAction/TwoPhaseCoordinator/ArjunaTransactionImple/AssumedCompleteServerTransaction",
                    "org.jboss.narayana.mgmt.internal.jts.ServerTransactionHeaderReader"
            ),
            new OSBTypeHandler(
                    true,
                    true,
                    "org.jboss.narayana.mgmt.internal.jts.RecoveredTransactionWrapper",
                    "org.jboss.narayana.mgmt.internal.jts.JTSActionBean",
                    "StateManager/BasicAction/TwoPhaseCoordinator/ArjunaTransactionImple/AssumedCompleteHeuristicTransaction",
                    null
            ),
            new OSBTypeHandler(
                    true,
                    true,
                    "org.jboss.narayana.mgmt.internal.jts.ServerTransactionWrapper",
                    "org.jboss.narayana.mgmt.internal.jts.JTSActionBean",
                    "StateManager/BasicAction/TwoPhaseCoordinator/ArjunaTransactionImple/AssumedCompleteHeuristicServerTransaction",
                    "org.jboss.narayana.mgmt.internal.jts.ServerTransactionHeaderReader"
            ),
            new OSBTypeHandler(
                    true,
                    true,
                    "org.jboss.narayana.mgmt.internal.jts.RecoveredTransactionWrapper",
                    "org.jboss.narayana.mgmt.internal.jts.JTSActionBean",
                    "StateManager/BasicAction/TwoPhaseCoordinator/ArjunaTransactionImple/AssumedCompleteTransaction",
                    null
            )
    };

    public static OSBTypeHandler[] getHandlers() {
        return jtsOsbTypes;
    }
}
