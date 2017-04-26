package olddemo;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.Uid;
import olddemo.actor.Booking;
import olddemo.actor.BookingException;
import olddemo.actor.BookingId;
import olddemo.actor.TaxiFirm;
import olddemo.internal.TaxiFirmImpl;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.jboss.stm.Container;

import java.util.List;

import static olddemo.ServerVerticle.CONTAINER_MODEL;
import static olddemo.ServerVerticle.CONTAINER_TYPE;
import static olddemo.ServerVerticle.HACK;
import static olddemo.ServerVerticle.RETRY_COUNT;

public class TaxiFirmVerticle extends BaseVerticle {
    static String TAXI_SLOT = "TAXI_SLOT";
    static String ALT_TAXI_SLOT = "ALT_TAXI_SLOT";
    static int DEFAULT_PORT = 8082;
    static int DEFAULT_ALT_PORT = 8082;

    private TaxiFirm service;

    TaxiFirmVerticle(String name, int port) {
        super(name, port);
    }

    protected void initServices() {
        LocalMap<String, String> map = vertx.sharedData().getLocalMap("olddemo.mymap");
        Container<TaxiFirm> theContainer;

        if (isExclusive()) {
            theContainer = new Container<>(Container.TYPE.PERSISTENT, Container.MODEL.EXCLUSIVE);
            service = theContainer.create(new TaxiFirmImpl(getName(), 20));
        } else {
            String slot = getName().equals("Alt") ? ALT_TAXI_SLOT : TAXI_SLOT;
            String uidName = map.get(slot);

            theContainer = new Container<>(CONTAINER_TYPE, CONTAINER_MODEL);

            if (uidName != null)
                service = theContainer.clone(new TaxiFirmImpl(getName(), 20), new Uid(uidName));
            else
                service = theContainer.create(new TaxiFirmImpl(getName(), 20));
        }

        advertiseServiceUid(TAXI_SLOT, theContainer.getIdentifier(service));
    }

    static TaxiFirm getOrCloneTaxiFirm(String uidName) {
        TaxiFirmImpl impl = new TaxiFirmImpl("Taxi", 40);

        if (isExclusive("Taxi")) {
            Container<TaxiFirm> container = new Container<>(Container.TYPE.PERSISTENT, Container.MODEL.EXCLUSIVE);
            return container.create(impl);
        } else {
            Container<TaxiFirm> container = new Container<>(CONTAINER_TYPE, CONTAINER_MODEL);

            if (uidName != null)
                return container.clone(impl, new Uid(uidName));
            else
                return container.create(impl);
        }
    }

    TaxiFirm getService() {
        return service;
    }

    protected void initRoutes(Router router) {
        router.route("/api/taxi*").handler(BodyHandler.create());

        router.get("/api/taxi").handler(this::getAll);
        router.post("/api/taxi/:reference/:seats").handler(this::addOne);
    }

    private void addOne(RoutingContext routingContext) {
        String reference =  routingContext.request().getParam("reference");
        String seats =  routingContext.request().getParam("seats");
        int noOfSeeats = seats == null ? 1 : Integer.valueOf(seats);

        try {
            BookingId id = bookTaxi(RETRY_COUNT, service, reference, noOfSeeats, "TaxiVerticle"); //service.bookShow(showName, noOfSeeats);
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

    static void hack(TaxiFirm taxiFirm) {
        if (HACK) {
            // begin hack
            AtomicAction A = new AtomicAction();
            A.begin();
            try {
                taxiFirm.initialize();
                A.commit();
            } catch (Exception e) {
                e.printStackTrace();
                A.abort();
            }
            // end hack
        }
    }

    static BookingId bookTaxi(int retryCnt, TaxiFirm taxiFirm, String reference, int noOfSeats, String debugMsg) throws BookingException {
        for (int i = 0; i < retryCnt; i++) {
            AtomicAction A = new AtomicAction();
            A.begin();
            try {
                BookingId bookingId = taxiFirm.bookTaxi(reference, noOfSeats);
                A.commit();
                System.out.printf("%s: TAXI booking listing succeeded after %d attempts%n", debugMsg, i);
                return bookingId;
            } catch (BookingException e) {
                System.out.printf("%s: TAXI booking error: %s%n", debugMsg, e.getMessage());
                A.abort();
                throw e;
            } catch (Exception e) {
                System.out.printf("%s: TAXI booking exception: %s%n", debugMsg, e.getMessage());
                A.abort();
            }
        }

        return null;
    }
}
