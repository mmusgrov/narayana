package org.jboss.narayana.rts.lra.annotation;

/**
 * The status of a compensator. The status is only valid after the coordinator has told the compensator to
 * complete or compensate. The name value of the enum should be returned by compensator methods marked with
 * the {@link Status} annotation.
 */
public enum CompensatorStatus {
    /**
     * the Compensator is currently compensating for the LRA
     */
    Compensating,
    /**
     * the Compensator has successfully compensated for the LRA
     */
    Compensated,
    /**
     * the Compensator was not able to compensate for the LRA (and must remember
     * it could not compensate until such time that it receives a forget message)
     */
    FailedToCompensate,
    /**
     * the Compensator is tidying up after being told to complete
     */
    Completing,
    /**
     * the Compensator has confirmed
     */
    Completed,
    /**
     * the Compensator was unable to tidy-up
     */
    FailedToComplete,
}
