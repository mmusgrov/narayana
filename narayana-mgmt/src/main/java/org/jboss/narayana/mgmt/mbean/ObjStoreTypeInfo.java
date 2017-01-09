package org.jboss.narayana.mgmt.mbean;

import org.jboss.narayana.mgmt.internal.arjuna.OSBTypeHandler;

public interface ObjStoreTypeInfo {
    OSBTypeHandler[] getHandlers();
}
