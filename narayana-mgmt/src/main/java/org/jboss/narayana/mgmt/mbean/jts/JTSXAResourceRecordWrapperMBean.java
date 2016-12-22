package org.jboss.narayana.mgmt.mbean.jts;

import org.jboss.narayana.mgmt.mbean.jta.XARecoveryResourceMBean;

/**
 * Created by tom on 01/11/2016.
 */
public interface JTSXAResourceRecordWrapperMBean extends XARecoveryResourceMBean {

    public void clearHeuristic() ;
}
