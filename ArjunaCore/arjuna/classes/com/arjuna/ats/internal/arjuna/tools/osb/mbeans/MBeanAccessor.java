package com.arjuna.ats.internal.arjuna.tools.osb.mbeans;

import com.arjuna.ats.arjuna.tools.osb.mbean.LogRecordWrapperMXBean;
import com.arjuna.ats.arjuna.tools.osb.util.JMXServer;

import javax.management.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MBeanAccessor {
    private ObjectInstance oi;
    private LogRecordWrapperMXBean proxy;
    private Map<String, Object> attributes;

    public MBeanAccessor(ObjectInstance oi, boolean createProxy) throws MBeanAccessorException {
        this.oi = oi;
        attributes = new HashMap<>();
        try {
            init(createProxy);
        } catch (OperationsException | ReflectionException e) {
            throw new MBeanAccessorException(e);
        }
    }

    public Set<String> getAttributeNames() {
        return attributes.keySet();
    }

    public Object getAttributeValue(String attributeName) {
        return attributes.get(attributeName);
    }

    public Object getCurrentAttributeValue(String attributeName) throws MBeanAccessorException {
        try {
            return JMXServer.getAgent().getServer().getAttribute(oi.getObjectName(), attributeName);
        } catch (OperationsException | ReflectionException | MBeanException e) {
            throw new MBeanAccessorException(e);
        }
    }
    private void init(boolean createProxy) throws OperationsException, ReflectionException {
        MBeanServer mbs = JMXServer.getAgent().getServer();

        MBeanInfo info = mbs.getMBeanInfo( oi.getObjectName() );

        initAttributes(mbs, info);

        if (createProxy)
            proxy = JMX.newMBeanProxy(mbs, oi.getObjectName(), LogRecordWrapperMXBean.class, true);
    }

    private void initAttributes(MBeanServer mbs, MBeanInfo info) throws ReflectionException, InstanceNotFoundException {
        MBeanAttributeInfo[] attributeArray = info.getAttributes();
        int i = 0;
        String[] attributeNames = new String[attributeArray.length];

        for (MBeanAttributeInfo ai : attributeArray)
            attributeNames[i++] = ai.getName();

        AttributeList attributeList = mbs.getAttributes(oi.getObjectName(), attributeNames);

        for (javax.management.Attribute attribute : attributeList.asList()) {
            attributes.put(attribute.getName(), attribute.getValue());
        }
    }

    private void initOperations(MBeanServer mbs, MBeanInfo info) {
        // Similarly for operations
        MBeanOperationInfo[] opArray = info.getOperations();
        int i = 0;
        String[] opNames = new String[opArray.length];

        for (MBeanOperationInfo ai : opArray) {
            opNames[i++] = ai.getName();
        }
    }

    public String getName() {
        return oi.getObjectName().getCanonicalName();
    }

    public LogRecordWrapperMXBean getProxy() {
        return proxy;
    }
}
