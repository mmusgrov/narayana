package org.jboss.narayana.mgmt.mbean.arjuna;

import javax.management.MBeanException;

import org.jboss.narayana.mgmt.annotation.MXBeanDescription;
import org.jboss.narayana.mgmt.annotation.MXBeanPropertyDescription;

@MXBeanDescription("")
public interface OSEntryBeanMBean extends ObjStoreItemMBean {
	String getType();
	String getId();

	@MXBeanPropertyDescription("Tell the Transaction Manager to remove this record")
	String remove() throws MBeanException;
}
