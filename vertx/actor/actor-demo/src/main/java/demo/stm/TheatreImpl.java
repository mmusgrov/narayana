package demo.stm;

import org.jboss.stm.annotations.ReadLock;
import org.jboss.stm.annotations.State;
import org.jboss.stm.annotations.WriteLock;

public class TheatreImpl implements Theatre {
    @State
    private int noOfCompletedActivities = 0;

    public TheatreImpl() {
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