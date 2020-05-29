
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/*
 * Dummy XA resource for testing
 */
public class DummyXA implements XAResource, Serializable {
    private static final long serialVersionUID = -2285367224867593569L;

    AtomicInteger prepareCnt = new AtomicInteger(0);
    AtomicInteger commitCnt = new AtomicInteger(0);
    AtomicInteger rollbackCnt = new AtomicInteger(0);

    private int _timeout;
    private boolean _print;

    public DummyXA () {
        this(true);
    }

    public DummyXA (boolean print) {
        _timeout = 0;  // no timeout
        _print = print;
    }

    public void commit (Xid xid, boolean onePhase) throws XAException {
        commitCnt.incrementAndGet();

        if (_print)
            System.out.println("DummyXA.commit called");
    }

    public void end (Xid xid, int flags) throws XAException {
        if (_print)
            System.out.println("DummyXA.end called");
    }

    public void forget (Xid xid) throws XAException {
        if (_print)
            System.out.println("DummyXA.forget called");
    }

    public int getTransactionTimeout () throws XAException {
        if (_print)
            System.out.println("DummyXA.getTransactionTimeout called");

        return _timeout;
    }

    public int prepare (Xid xid) throws XAException {
        prepareCnt.incrementAndGet();

        if (_print)
            System.out.println("DummyXA.prepare called");

        return XAResource.XA_OK;
    }

    public Xid[] recover (int flag) throws XAException {
        if (_print)
            System.out.println("DummyXA.recover called");

        return null;
    }

    public void rollback (Xid xid) throws XAException {
        rollbackCnt.incrementAndGet();

        if (_print)
            System.out.println("DummyXA.rollback called");
    }

    public boolean setTransactionTimeout (int seconds) throws XAException {
        if (_print)
            System.out.println("DummyXA.setTransactionTimeout called");

        _timeout = seconds;

        return true;
    }

    public void start (Xid xid, int flags) throws XAException {
        if (_print)
            System.out.println("DummyXA.start called");
    }

    public boolean isSameRM (XAResource xares) throws XAException {
        if (_print)
            System.out.println("DummyXA.isSameRM called");

        return (xares == this);
    }
}
