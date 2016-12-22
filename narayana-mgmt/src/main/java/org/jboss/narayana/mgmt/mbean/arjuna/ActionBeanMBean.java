package org.jboss.narayana.mgmt.mbean.arjuna;

import javax.management.MBeanException;

import org.jboss.narayana.mgmt.annotation.MXBeanDescription;
import org.jboss.narayana.mgmt.annotation.MXBeanPropertyDescription;

@MXBeanDescription("Management view of a transaction")
public interface ActionBeanMBean extends OSEntryBeanMBean {
	long getAgeInSeconds();
	String getCreationTime();
	@MXBeanPropertyDescription("Indicates whether this entry corresponds to a transaction participant")
	boolean isParticipant();

	@MXBeanPropertyDescription("Tell the Transaction Manager to remove this action")
	String remove() throws MBeanException;
}
