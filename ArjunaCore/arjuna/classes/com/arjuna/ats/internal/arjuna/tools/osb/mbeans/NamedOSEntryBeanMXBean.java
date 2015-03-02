package com.arjuna.ats.internal.arjuna.tools.osb.mbeans;

import com.arjuna.ats.arjuna.tools.osb.annotation.MXBeanDescription;
import com.arjuna.ats.arjuna.tools.osb.mbean.OSEntryBeanMXBean;

import javax.management.NotificationBroadcaster;

@MXBeanDescription("Management view of a named transaction log entry")
public interface NamedOSEntryBeanMXBean extends OSEntryBeanMXBean {
    String getName();
}
