package org.jboss.narayana.mgmt.mbean.jts;

import org.jboss.narayana.mgmt.annotation.MXBeanDescription;
import org.jboss.narayana.mgmt.mbean.jta.XAResourceMBean;

import javax.management.MXBean;

@MXBean
@MXBeanDescription("Management view of an XAResource participating in a transaction")
public interface XAResourceRecordBeanMBean extends XAResourceMBean {
}
