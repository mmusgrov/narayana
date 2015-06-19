package com.arjuna.ats.arjuna.tools.osb.mbean;

import com.arjuna.ats.arjuna.tools.osb.annotation.MXBeanDescription;
import com.arjuna.ats.arjuna.tools.osb.annotation.MXBeanPropertyDescription;

/**
 *
 * @Deprecated as of 4.17.26.Final In a subsequent release we will change packages names in order to 
 * provide a better separation between public and internal classes.
 */
@Deprecated // in order to provide a better separation between public and internal classes.
@MXBeanDescription("Management view of a transaction")
public interface ActionBeanMBean extends OSEntryBeanMBean {
	long getAgeInSeconds();
	String getCreationTime();
	@MXBeanPropertyDescription("Indicates whether this entry corresponds to a transaction participant")
	boolean isParticipant();

	@MXBeanPropertyDescription("Tell the Transaction Manager to remove this action")
	String remove();
}