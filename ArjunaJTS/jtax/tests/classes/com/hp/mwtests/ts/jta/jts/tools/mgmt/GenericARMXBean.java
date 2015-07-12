package com.hp.mwtests.ts.jta.jts.tools.mgmt;

import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;

import javax.management.*;

public class GenericARMXBean implements NamedOSEntryBeanMXBean, NotificationBroadcaster {

    private String name;
    private String type;
    private String id;

    private NotificationBroadcasterSupport changeNotifier = new NotificationBroadcasterSupport();
    private long notificationId = 0;

    public GenericARMXBean(String type, String id) {
        this.name = ObjStoreMBeanON.generateObjectName(type, id);
        this.type = type;
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
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
