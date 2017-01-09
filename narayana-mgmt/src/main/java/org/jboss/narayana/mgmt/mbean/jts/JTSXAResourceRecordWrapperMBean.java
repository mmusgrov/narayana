package org.jboss.narayana.mgmt.mbean.jts;

import org.jboss.narayana.mgmt.mbean.jta.XARecoveryResourceMBean;

import javax.management.MXBean;

/**
 * Created by tom on 01/11/2016.
 */
@MXBean
public interface JTSXAResourceRecordWrapperMBean extends XARecoveryResourceMBean {

    public void clearHeuristic() ;
}
