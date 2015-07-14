package com.hp.mwtests.ts.jta.jts.tools.mgmt;

import com.arjuna.ats.arjuna.tools.osb.annotation.MXBeanDescription;
import com.arjuna.ats.arjuna.tools.osb.annotation.MXBeanPropertyDescription;
import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;

@MXBeanDescription("Representation of a transaction participant")
public interface LogRecordWrapperMXBean extends NamedOSEntryBeanMXBean {//}, LogRecordWrapperMBean {
	@MXBeanPropertyDescription("Indication of the status of this transaction participant (prepared, heuristic, etc)")
	String getStatus();

	//@MXBeanPropertyDescription("Change the status of this participant back to prepared or to a heuristic")
	void setStatus(String newState);

	@MXBeanPropertyDescription("Is this participant in a heuristic state")
	boolean isHeuristic();

    @MXBeanPropertyDescription("Clear any heuristics so that the recovery system will replay the commit")
    String clearHeuristic();

	@MXBeanPropertyDescription("The internal type of this transaction participant")
	String getType();

	@MXBeanPropertyDescription("This entry corresponds to a transaction participant")
	boolean isParticipant();

	// TODO create an MBean to represent the different types of heuristics
	@MXBeanPropertyDescription("If this record represents a heuristic then report the type of the heuristic")
	String getHeuristicStatus();
}
