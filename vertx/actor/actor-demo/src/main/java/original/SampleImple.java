package original;

import org.jboss.stm.annotations.ReadLock;
import org.jboss.stm.annotations.State;
import org.jboss.stm.annotations.WriteLock;

public class SampleImple implements Sample
{
    SampleImple ()
    {
        this(0);
    }

    SampleImple (int init)
    {
        _isState = init;
    }

    @ReadLock
    public int value ()
    {
        return _isState;
    }

    @WriteLock
    public void increment ()
    {
        _isState++;
    }

    @WriteLock
    public void decrement ()
    {
        _isState--;
    }

    @State
    private int _isState;
}