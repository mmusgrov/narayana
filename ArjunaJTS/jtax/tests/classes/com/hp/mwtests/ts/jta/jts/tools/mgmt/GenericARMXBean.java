package com.hp.mwtests.ts.jta.jts.tools.mgmt;

import com.arjuna.ats.internal.arjuna.tools.osb.mbeans.NamedOSEntryBeanMXBean;

public class GenericARMXBean implements NamedOSEntryBeanMXBean {
    public static String AR_BEAN_NAME_FMT = "%s,itype=%s,uid=%s";

    private String name;
    private String type;
    private String id;

    public static String generateObjectName(String type, String id) {
        return String.format(GenericARMXBean.AR_BEAN_NAME_FMT, JMXServer.STORE_MBEAN_NAME, type, id);
    }

    public GenericARMXBean(String type, String id) {
        this.name = GenericARMXBean.generateObjectName(type, id);
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
        return null;
    }
}
