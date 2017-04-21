package demo;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.Uid;
import demo.actor.Booking;
import demo.actor.TaxiFirm;
import demo.internal.TaxiFirmImpl;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.shareddata.LocalMap;
import org.jboss.stm.Container;

import java.util.List;

import static demo.TripVerticle.CONTAINER_MODEL;
import static demo.TripVerticle.CONTAINER_TYPE;
import static demo.TripVerticle.HACK;
import static demo.TripVerticle.RETRY_COUNT;

public class TaxiVerticle extends AbstractVerticle {

    public void start()
    {
        LocalMap<String, String> map = vertx.sharedData().getLocalMap("demo1.mymap");
        Container<TaxiFirm> theContainer = new Container<>(CONTAINER_TYPE, CONTAINER_MODEL);

        String uidName = map.get(TripVerticle.TAXI_SLOT);

        TaxiFirm taxi = theContainer.clone(new TaxiFirmImpl("Taxi", 40), new Uid(uidName));

//        hack(taxi);

        bookTaxi(RETRY_COUNT, taxi, 2, "TaxiVerticle");
    }

    static void hack(TaxiFirm taxi) {
        if (HACK) {
            // begin hack
            AtomicAction A = new AtomicAction();
            A.begin();
            try {
                taxi.initialize();
                A.commit();
            } catch (Exception e) {
                A.abort();
            }
            // end hack
        }
    }

    static public void bookTaxi(int retryCnt, TaxiFirm taxi, int noOfSeats, String debugMsg) {
        for (int i = 0; i < retryCnt; i++) {
            AtomicAction A = new AtomicAction();
            A.begin();
            try {
                taxi.bookTaxi(noOfSeats);
                A.commit();
                System.out.printf("%s: TAXI booking succeeded after %d attempts%n", debugMsg, i);
                return;
            } catch (Exception e) {
                System.out.printf("%s: TAXI booking failed: %s%n", debugMsg, e.getMessage());
                A.abort();
            }
        }
    }

    static List<Booking> getBookings(int retryCnt, TaxiFirm taxi, List<Booking> bookings, String debugMsg) {
        for (int i = 0; i < retryCnt; i++) {
            AtomicAction A = new AtomicAction();
            A.begin();
            try {
                taxi.getBookings(bookings);
                A.commit();
                System.out.printf("%s: TAXI booking listing succeeded after %d attempts%n", debugMsg, i);
                break;
            } catch (Exception e) {
                System.out.printf("%s: TAXI booking listing exception: %s%n", debugMsg, e.getMessage());
                A.abort();
            }
        }

        return bookings;
    }
}
