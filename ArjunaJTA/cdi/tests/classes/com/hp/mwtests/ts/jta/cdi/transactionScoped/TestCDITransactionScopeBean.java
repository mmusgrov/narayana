package com.hp.mwtests.ts.jta.cdi.transactionScoped;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.transaction.TransactionScoped;
import java.io.Serializable;

/**
 * @author paul.robinson@redhat.com 01/05/2013
 */
@Named("TestCDITransactionScopeBean")
@TransactionScoped
public class TestCDITransactionScopeBean implements Serializable {

    private static boolean preDestroyCalled;

    private boolean postConstructCalled;

    private int value = 0;

    @PostConstruct
    public void postConstruct() {
        postConstructCalled = true;
    }
    public int getValue() {

        return value;
    }

    public void setValue(int value) {

        this.value = value;
    }

    public boolean isPostConstructCalled() {
        return postConstructCalled;
    }

    public static boolean isPreDestroyCalled() {
        return preDestroyCalled;
    }

    public static void setPreDestroyCalled(boolean preDestroyCalled) {
        TestCDITransactionScopeBean.preDestroyCalled = preDestroyCalled;
    }

    @PreDestroy
    public void preDestroy() {
        preDestroyCalled = true;
    }
}