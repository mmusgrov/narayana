package org.jboss.narayana.mgmt.internal.arjuna;

/**
 * Enumeration of the commit status of a participant in an action/transaction
 */
public enum ParticipantStatus {PREPARED, PENDING, FAILED, READONLY, HEURISTIC}
