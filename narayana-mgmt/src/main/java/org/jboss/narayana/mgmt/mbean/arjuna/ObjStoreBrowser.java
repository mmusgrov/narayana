package org.jboss.narayana.mgmt.mbean.arjuna;

import javax.management.MBeanException;

public class ObjStoreBrowser implements ObjStoreBrowserMBean {
    private org.jboss.narayana.mgmt.internal.arjuna.ObjStoreBrowser impl;

    public ObjStoreBrowser(org.jboss.narayana.mgmt.internal.arjuna.ObjStoreBrowser impl) {
        this.impl = impl;
    }

    @Override
    public void probe() throws MBeanException {
        impl.probe();
    }

    @Override
    public void viewSubordinateAtomicActions(boolean enable) {
        impl.viewSubordinateAtomicActions(enable);
    }

    @Override
    public void setExposeAllRecordsAsMBeans(boolean exposeAllLogs) {
         impl.setExposeAllRecordsAsMBeans(exposeAllLogs);
    }
}
