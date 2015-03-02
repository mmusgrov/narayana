package com.arjuna.ats.internal.arjuna.tools.osb.mbeans;

import javax.management.*;

public class NamedOSEntryBeanMBeanImpl implements NamedOSEntryBeanMXBean, NotificationBroadcaster {
    private String type;
    private String name;
    private String id;
    private NotificationBroadcasterSupport changeNotifier = new NotificationBroadcasterSupport();
    private long notificationId = 0;

    public NamedOSEntryBeanMBeanImpl(String type, String name, String id) {
        this.type = type;
        this.name = name;
        this.id = id;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String remove() {
        changeNotifier.sendNotification(new Notification("remove", this, notificationId++, type + "." + id));
        return "in progress";
    }

    @Override
    public String getName() { return name; }

    public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
        changeNotifier.addNotificationListener(listener, filter, handback);
    }

    public void removeNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
        changeNotifier.removeNotificationListener(listener);
    }

    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[] {
            new MBeanNotificationInfo(new String[] {"remove"}, Notification.class.getName(), "MBean remove notifications.")
        };
    }
}
