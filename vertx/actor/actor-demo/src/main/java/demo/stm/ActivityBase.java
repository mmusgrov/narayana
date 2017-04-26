package demo.stm;

import com.arjuna.ats.arjuna.AtomicAction;
import org.jboss.stm.Container;
import org.jboss.stm.LockException;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

class ActivityBase {
    void initializeSTMObject(Theatre activity) {
        AtomicAction A = new AtomicAction();

        A.begin();
        activity.init();
        A.commit();
    }

    void updateInDifferentThread(Container<Theatre> containter, Theatre mandatory) {

        Callable<Integer> task = () -> {
            Theatre mandatoryClone = containter.clone(new TheatreImpl(), mandatory);

            AtomicAction B = new AtomicAction();
            B.begin();
            mandatoryClone.activity();
            int value = mandatoryClone.getValue();
            B.commit();

            return value;
        };

        Future<Integer> future = Executors.newCachedThreadPool().submit(task);

        try {
            int value = future.get(); // the mandatory changes made in activity1 should not be visible from another thread

            assertEquals(value, mandatory.getValue());
        } catch (Exception e) {
            // expect a LockException since this thread should already have the WriteLock
            assertTrue(e.getMessage(), (e.getCause() instanceof LockException));
            fail("updateInDifferentThread failed with " + e.getMessage());
        }
    }
}
