package org.jboss.narayana.mgmt.mbean.arjuna;

import javax.management.MBeanException;
import javax.management.MXBean;

import org.jboss.narayana.mgmt.annotation.MXBeanDescription;
import org.jboss.narayana.mgmt.annotation.MXBeanPropertyDescription;

@MXBeanDescription("")
@MXBean
public interface OSEntryBeanMBean extends ObjStoreItemMBean {
	String getType();
	String getId();

	@MXBeanPropertyDescription("Tell the Transaction Manager to remove this record")
	String remove() throws MBeanException;
}
