package demo.stm;

import org.jboss.stm.annotations.ReadLock;
import org.jboss.stm.annotations.State;
import org.jboss.stm.annotations.WriteLock;

public class TaxiServiceImpl implements TaxiService {
    @State
    private int noOfCompletedActivities = 0;

    @Override
    @WriteLock
    public void failingActivity()  throws Exception {
//            activity(); // TODO state changes made inside a TopLevelAction are not rolled back on exception
        throw new Exception();
    }

    @Override
    @WriteLock
    public void init() {
    }

    @Override
    @WriteLock
    public void activity() {
        noOfCompletedActivities += 1;
    }

    @Override
    @ReadLock
    public int getValue() {
        return noOfCompletedActivities;
    }
}
