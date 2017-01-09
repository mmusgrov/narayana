package org.jboss.narayana.mgmt.mbean.jta;

import javax.management.MBeanException;

/**
 * Created by mmusgrov on 31/12/16.
 */
public class RecoverConnectableAtomicActionBean implements RecoverConnectableAtomicActionBeanMBean {
    private org.jboss.narayana.mgmt.internal.jta.RecoverConnectableAtomicActionBean impl;

    public RecoverConnectableAtomicActionBean(org.jboss.narayana.mgmt.internal.jta.RecoverConnectableAtomicActionBean impl) {
        this.impl = impl;
    }

    @Override
    public String getType() {
        return impl.getType();
    }

    @Override
    public String getId() {
        return impl.getId();
    }

    @Override
    public long getAgeInSeconds() {
        return impl.getAgeInSeconds();
    }

    @Override
    public String getCreationTime() {
        return impl.getCreationTime();
    }

    @Override
    public boolean isParticipant() {
        return impl.isParticipant();
    }

    @Override
    public String remove() throws MBeanException {
        return impl.remove();
    }

    @Override
    public String toDo() {
        return impl.toDo();
    }
}
