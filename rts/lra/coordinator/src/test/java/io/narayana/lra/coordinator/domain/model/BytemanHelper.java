package io.narayana.lra.coordinator.domain.model;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public class BytemanHelper {
    static AtomicBoolean businessCalled = new AtomicBoolean(false);
    public void rendezvousWait() throws InterruptedException {
        synchronized (businessCalled) {
            businessCalled.set(true);
            businessCalled.notifyAll();
            businessCalled.wait();
        }
    }
    public void rendezvousNotify() throws InterruptedException {
        synchronized (businessCalled) {
            while (!businessCalled.get()) {
                businessCalled.wait();
            }
            businessCalled.notify();
        }
    }
    public void abortLRA(LongRunningAction lra) throws NoSuchMethodException, InvocationTargetException,
            IllegalAccessException {
        Method method = lra.getClass().getDeclaredMethod("abortLRA");
        method.setAccessible(true);
        method.invoke(lra);
    }
}
