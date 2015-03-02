package com.arjuna.ats.internal.arjuna.tools.osb.mbeans;

import javax.management.*;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class TypeSafeJMXProxy {

    /** There are no instances of this class. */
    private TypeSafeJMXProxy() {
    }

    /**
     * Create an MBean proxy, checking that the target MBean exists
     * and implements the attributes and operations defined by the
     * given interface.
     *
     * @param mbsc the MBean Server in which the proxied MBean is registered.
     * @param name the ObjectName under which the proxied MBean is registered.
     * @param intfClass the MBean interface that the proxy will
     * implement by forwarding its methods to the proxied MBean.
     *
     * @return The newly-created proxy.
     *
     * @throws IOException if there is a communication problem when
     * connecting to the {@code MBeanServerConnection}.
     * @throws InstanceNotFoundException if there is no MBean
     * registered under the given {@code name}.
     * @throws NotCompliantMBeanException if {@code intfClass} is
     * not a valid MBean interface.
     * @throws NoSuchMethodException if a method in
     * {@code intfClass} does not correspond to an attribute or
     * operation in the proxied MBean.
     */
    public static <T> T newMBeanProxy(
            MBeanServerConnection mbsc,
            ObjectName name,
            Class<T> intfClass)
            throws IOException, InstanceNotFoundException,
            NotCompliantMBeanException, NoSuchMethodException {

        // Get the MBeanInfo, or throw InstanceNotFoundException
        final MBeanInfo mbeanInfo;
        try {
            mbeanInfo = mbsc.getMBeanInfo(name);
        } catch (InstanceNotFoundException e) {
            throw e;
        } catch (JMException e) {
            // IntrospectionException or ReflectionException    public class TypeSafeJMXProxy {
            // IntrospectionException or ReflectionException:
            // very improbable in practice so just pretend the MBean wasn't there
            // but keep the real exception in the exception chain
            final String msg = "Exception getting MBeanInfo for " + name;
            InstanceNotFoundException infe = new InstanceNotFoundException(msg);
            infe.initCause(e);
            throw infe;
        }

        // Construct the MBeanInfo that we would expect from a Standard MBean
        // implementing intfClass.  We need a non-null implementation of intfClass
        // so we create a proxy that will never be invoked.
        final T impl = intfClass.cast(Proxy.newProxyInstance(
                intfClass.getClassLoader(), new Class<?>[]{intfClass}, nullIH));
        final StandardMBean mbean = new StandardMBean(impl, intfClass);
        final MBeanInfo proxyInfo = mbean.getMBeanInfo();

//        checkMBeanInfos(intfClass.getClassLoader(), proxyInfo, mbeanInfo);
        return intfClass.cast(MBeanServerInvocationHandler.newProxyInstance(
                mbsc, name, intfClass, false));
    }

    private static class NullInvocationHandler implements InvocationHandler {
        public Object invoke(Object proxy, Method method, Object[] args) {
            return null;
        }
    }

    private static final NullInvocationHandler nullIH =  new NullInvocationHandler();

}
