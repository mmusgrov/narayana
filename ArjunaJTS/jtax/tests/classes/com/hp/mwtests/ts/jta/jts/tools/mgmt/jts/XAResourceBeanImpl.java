package com.hp.mwtests.ts.jta.jts.tools.mgmt.jts;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.arjuna.tools.osb.mbean.HeuristicStatus;
import com.arjuna.ats.jta.xa.XATxConverter;
import com.arjuna.ats.jta.xa.XidImple;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.BasicActionMXBean;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.LogRecordBean;
import com.hp.mwtests.ts.jta.jts.tools.mgmt.ParticipantStatus;

import java.io.IOException;

// TODO this is not a compliant MBean since it implements multiple MXBean interfaces (maybe investigate using a
// DynamicMBean
public class XAResourceBeanImpl extends LogRecordBean implements XAResourceBeanMXBean {
    JTSXAResourceRecordWrapper xares;
    String className = "unavailable";
    String eisProductName = "unavailable";
    String eisProductVersion = "unavailable";
    String jndiName = "unavailable";
    int timeout = 0;
    com.arjuna.ats.internal.jta.tools.osb.mbean.jts.JTSXAResourceRecordWrapper jtsXAResourceRecord;
    XidImple xidImple;
    int heuristic;
    Uid uid;

    public XAResourceBeanImpl(String type, String name, String id) {
        super(type, name, id);

        init();
    }

    public XAResourceBeanImpl(String parentObjectName, BasicActionMXBean baa, AbstractRecord ar, ParticipantStatus listType) {
        super(baa, ar, listType);

        init();
    }

    private void init() {
        uid = new Uid(getId());
        jndiName = uid.stringForm();
        className = "unavailable";
        eisProductName = "unavailable";
        eisProductVersion = "unavailable";
        timeout = 0;
        xares = new JTSXAResourceRecordWrapper(uid);
        xidImple = xares.xidImple;
        heuristic = xares.heuristic;
    }

    public String getClassName() { return className; }
    public String getEisProductName() { return eisProductName; }
    public String getEisProductVersion() { return eisProductVersion; }
    public String getJndiName() { return jndiName; }
    public int getTimeout() { return timeout; }

    @Override
    public String getHeuristicStatus() {
        return HeuristicStatus.intToStatus(xares.heuristic).name();
    }

    @Override
    public byte[] getGlobalTransactionId() {
        return xidImple.getGlobalTransactionId();
    }
    @Override
    public byte[] getBranchQualifier() {
        return xidImple.getBranchQualifier();
    }
    @Override
    public int getFormatId() {
        return xidImple.getFormatId();
    }
    @Override
    public String getNodeName() {
        return XATxConverter.getNodeName(xidImple.getXID());
    }
    @Override
    public int getHeuristicValue() {
        return heuristic;
    }

    public void setJtsXAResourceRecord(com.arjuna.ats.internal.jta.tools.osb.mbean.jts.JTSXAResourceRecordWrapper jtsXAResourceRecord) {
        this.jtsXAResourceRecord = jtsXAResourceRecord;
    }

    @Override
    public String remove() {
        if (jtsXAResourceRecord != null && jtsXAResourceRecord.doRemove())
            jtsXAResourceRecord = null;

        return super.remove();
    }

    /**
     * Extension of an XAResource record for exposing the underlying XAResource which is protected
     */
    public class JTSXAResourceRecordWrapper extends com.arjuna.ats.internal.jta.resources.jts.orbspecific.XAResourceRecord {
        XidImple xidImple;
        int heuristic;

        public JTSXAResourceRecordWrapper(Uid uid) {
            super(uid);

            xidImple = new XidImple(getXid());

            if (_theXAResource != null) {
                XAResourceBeanImpl.this.className = _theXAResource.getClass().getName();
                XAResourceBeanImpl.this.jndiName = callMethod(_theXAResource, "getJndiName");
                XAResourceBeanImpl.this.eisProductName = callMethod(_theXAResource, "getProductName");
                XAResourceBeanImpl.this.eisProductVersion = callMethod(_theXAResource, "getProductVersion");

                try {
                    timeout = _theXAResource.getTransactionTimeout();
                } catch (Exception e) {
                }
            }
        }

        public boolean restoreState(InputObjectState os) {
            InputObjectState copy = new InputObjectState(os);
            try {
                heuristic = copy.unpackInt();
            } catch (IOException e) {
            }

            return super.restoreState(os);
        }

        public String callMethod(Object object, String mName) {
            try {
                return (String) object.getClass().getMethod(mName).invoke(object);
            } catch (NoSuchMethodException e) {
                return "Not supported";
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }
    }
}
