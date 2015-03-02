package com.arjuna.ats.internal.arjuna.tools.osb.mbeans;

import javax.management.JMException;

public class MBeanAccessorException extends Exception {
    public MBeanAccessorException(JMException e) {
        super(e);
    }
}
