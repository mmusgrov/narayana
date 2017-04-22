package demo;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.Uid;
import demo.actor.Booking;
import demo.actor.BookingException;
import demo.actor.BookingId;
import demo.actor.Theatre;
import demo.internal.TheatreImpl;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.jboss.stm.Container;

import java.util.List;

import static demo.ServerVerticle.CONTAINER_MODEL;
import static demo.ServerVerticle.CONTAINER_TYPE;
import static demo.ServerVerticle.HACK;
import static demo.ServerVerticle.RETRY_COUNT;

public class TheatreVerticle extends BaseVerticle {
    static String THEATRE_SLOT = "THEATRE_SLOT";
    static int DEFAULT_PORT = 8081;

    private Theatre service;

    TheatreVerticle(String name, int port) {
        super(name, port);
    }

    protected void initServices() {
        LocalMap<String, String> map = vertx.sharedData().getLocalMap("olddemo.mymap");
        Container<Theatre> theContainer;

        if (isExclusive()) {
            theContainer = new Container<>(Container.TYPE.PERSISTENT, Container.MODEL.EXCLUSIVE);
            service = theContainer.create(new TheatreImpl("Theatre", 50));
        } else {
            String uidName = map.get(THEATRE_SLOT);

            theContainer = new Container<>(CONTAINER_TYPE, CONTAINER_MODEL);

            if (uidName != null)
                service = theContainer.clone(new TheatreImpl("Theatre", 50), new Uid(uidName));
            else
                service = theContainer.create(new TheatreImpl("Theatre", 50));
        }

        advertiseServiceUid(THEATRE_SLOT, theContainer.getIdentifier(service));
    }

    static Theatre getOrCloneTheatre(String uidName) {
        Theatre impl = new TheatreImpl("Theatre", 50);

        if (isExclusive("Theatre")) {
            Container<Theatre> container = new Container<>(Container.TYPE.PERSISTENT, Container.MODEL.EXCLUSIVE);
            return container.create(impl);
        } else {
            Container<Theatre> container = new Container<>(CONTAINER_TYPE, CONTAINER_MODEL);

            if (uidName != null)
                return container.clone(impl, new Uid(uidName));
            else
                return container.create(impl);
        }
    }

    Theatre getService() {
        return service;
    }

    protected void initRoutes(Router router) {
        router.route("/api/theatre*").handler(BodyHandler.create());

        router.get("/api/theatre").handler(this::getAll);
        router.post("/api/theatre/:show/:showseats").handler(this::addOne);
    }

    private void addOne(RoutingContext routingContext) {
        String showName =  routingContext.request().getParam("show");
        String showSeats =  routingContext.request().getParam("showseats");

        int noOfShowSeats = showSeats == null ? 1 : Integer.valueOf(showSeats);

        try {
            BookingId id = bookShow(RETRY_COUNT, service, showName, noOfShowSeats, "TheatreVerticle"); //service.bookShow(showName, noOfSeeats);
            Booking booking = service.getBooking(id);

            routingContext.response()
                    .setStatusCode(201)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(booking));
        } catch (BookingException e) {
            routingContext.response()
                    .setStatusCode(406)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(new JsonObject().put("Status", e.getMessage()).encode());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void getBookings(List bookings) {
        service.getBookings(bookings);
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

    static BookingId bookShow(int retryCnt, Theatre theatre, String showName, int noOfSeats, String debugMsg) throws BookingException {
        for (int i = 0; i < retryCnt; i++) {
            AtomicAction A = new AtomicAction();
            A.begin();
            try {
                BookingId bookingId = theatre.bookShow(showName, noOfSeats);
                A.commit();
                System.out.printf("%s: THEATRE booking listing succeeded after %d attempts%n", debugMsg, i);
                return bookingId;
            } catch (BookingException e) {
                System.out.printf("%s: THEATRE booking error: %s%n", debugMsg, e.getMessage());
                A.abort();
                throw e;
            } catch (Exception e) {
                System.out.printf("%s: THEATRE booking exception: %s%n", debugMsg, e.getMessage());
                A.abort();
            }
        }

        return null;
    }
}
