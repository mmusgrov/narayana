package reproducer;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.Uid;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.shareddata.LocalMap;
import org.jboss.stm.Container;

import reproducer.actor.Booking;
import reproducer.actor.BookingException;
import reproducer.actor.Theatre;
import reproducer.internal.TheatreImpl;

import java.util.ArrayList;
import java.util.List;

import static reproducer.TripVerticle.CONTAINER_MODEL;
import static reproducer.TripVerticle.CONTAINER_TYPE;
import static reproducer.TripVerticle.HACK;
import static reproducer.TripVerticle.RETRY_COUNT;

public class TheatreVerticle extends AbstractVerticle {

    public void start()
    {
        LocalMap<String, String> map = vertx.sharedData().getLocalMap("olddemo.mymap");
        String uidName = map.get(TripVerticle.THEATRE_SLOT);
        Container<Theatre> theContainer;
        Theatre theatre;
        boolean exclusive = Boolean.getBoolean("theatre.exclusive");

        // persistent and shared: valid combination
        // persistent and exclusive: valid combination
        // recoverable and exclusive: valid combination
        // recoverable and shared: invalid combination

        if (exclusive) {
            theContainer = new Container<>(Container.TYPE.PERSISTENT, Container.MODEL.EXCLUSIVE);
            theatre = theContainer.create(new TheatreImpl("Theatre", 50));
        } else {
            theContainer = new Container<>(CONTAINER_TYPE, CONTAINER_MODEL);
            theatre = theContainer.clone(new TheatreImpl("Theatre", 50), new Uid(uidName));
        }

//        hack(theatre);

        bookShow(RETRY_COUNT, theatre, "Cats", 4, "TheatreVerticle");

        System.out.printf("=== THEATRE: Bookings:%n");

        List<Booking> bookings = getBookings(RETRY_COUNT, theatre, new ArrayList<>(), "TheatreVerticle");

        for (Booking booking : bookings) {
            System.out.printf("\t%s booking for %s for %d%n",
                    booking.getName(), booking.getDescription(), booking.getSize());
        }
    }

    static void hack(Theatre theatre) {
        if (HACK) {
            // begin hack
            AtomicAction A = new AtomicAction();
            A.begin();
            try {
                theatre.initialize();
                A.commit();
            } catch (Exception e) {
                e.printStackTrace();
                A.abort();
            }
            // end hack
        }
    }

    static void bookShow(int retryCnt, Theatre theatre, String showName, int noOfSeats, String debugMsg) {
        for (int i = 0; i < retryCnt; i++) {
            AtomicAction A = new AtomicAction();
            A.begin();
            try {
                theatre.bookShow(showName, noOfSeats);
                A.commit();
                System.out.printf("%s: THEATRE booking listing succeeded after %d attempts%n", debugMsg, i);
                return;
            } catch (BookingException e) {
                System.out.printf("%s: THEATRE booking error: %s%n", debugMsg, e.getMessage());
                A.abort();
            } catch (Exception e) {
                System.out.printf("%s: THEATRE booking exception: %s%n", debugMsg, e.getMessage());
                A.abort();
            }
        }
    }

    static List<Booking> getBookings(int retryCnt, Theatre theatre, List<Booking> bookings, String debugMsg) {
        for (int i = 0; i < retryCnt; i++) {
            AtomicAction A = new AtomicAction();
            A.begin();
            try {
                theatre.getBookings(bookings);
                A.commit();
                System.out.printf("%s: THEATRE booking listing succeeded after %d attempts%n", debugMsg, i);
                break;
            } catch (Exception e) {
                System.out.printf("%s: THEATRE booking listing exception: %s%n", debugMsg, e.getMessage());
                A.abort();
            }
        }

        return bookings;
    }
}
