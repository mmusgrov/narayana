package olddemo;

import olddemo.actor.Booking;
import olddemo.actor.BookingException;
import olddemo.actor.BookingId;
import olddemo.actor.TaxiFirm;
import olddemo.actor.Theatre;
import olddemo.actor.Trip;
import olddemo.internal.TripImpl;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.jboss.stm.Container;

import java.util.ArrayList;
import java.util.List;

import static olddemo.ServerVerticle.CONTAINER_MODEL;
import static olddemo.ServerVerticle.CONTAINER_TYPE;
import static olddemo.ServerVerticle.RETRY_COUNT;
import static olddemo.TaxiFirmVerticle.ALT_TAXI_SLOT;
import static olddemo.TaxiFirmVerticle.TAXI_SLOT;
import static olddemo.TheatreVerticle.THEATRE_SLOT;

public class TripVerticle extends BaseVerticle {
    static int DEFAULT_PORT = 8080;

    private Trip service;

    private Theatre theatre;
    private TaxiFirm taxi;
    private TaxiFirm altTaxi;

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        Future<Void> theatreReady = Future.future();
        Future<Void> taxiReady = Future.future();
        Future<Void> altTaxiReady = Future.future();

        TheatreVerticle theatreVerticle = new TheatreVerticle("Theatre", TheatreVerticle.DEFAULT_PORT);
        TaxiFirmVerticle taxiFirmVerticle =  new TaxiFirmVerticle("Favorite", TaxiFirmVerticle.DEFAULT_PORT);
        TaxiFirmVerticle altTaxiFirmVerticle = new TaxiFirmVerticle("Alt", TaxiFirmVerticle.DEFAULT_ALT_PORT);

        vertx.deployVerticle(theatreVerticle, getCompletionHandler(theatreReady));
        vertx.deployVerticle(taxiFirmVerticle, getCompletionHandler(taxiReady));
        vertx.deployVerticle(altTaxiFirmVerticle, getCompletionHandler(altTaxiReady));

        CompositeFuture.join(theatreReady, taxiReady, altTaxiReady).setHandler(ar -> {
                    if (ar.succeeded()) {
                        vertx.deployVerticle(new TripVerticle("Trip", DEFAULT_PORT));
                    } else {
                        System.out.printf("=== TRIP: Could not start all services: %s%n", ar.cause().getMessage());
                    }
                }
        );
    }

    private static Handler<AsyncResult<String>> getCompletionHandler(Future<Void> future) {
        return (AsyncResult<String> res) -> {
            if (res.succeeded())
                future.complete();
            else
                future.fail(res.cause());
        };
    }

    TripVerticle(String name, int port) {
        super(name, port);
    }

    protected void initServices() {
        this.theatre = TheatreVerticle.getOrCloneTheatre(getServiceUid(THEATRE_SLOT));
        this.taxi = TaxiFirmVerticle.getOrCloneTaxiFirm(getServiceUid(TAXI_SLOT));
        this.altTaxi = TaxiFirmVerticle.getOrCloneTaxiFirm(getServiceUid(ALT_TAXI_SLOT));

        if (isExclusive()) {
            Container<Trip> theContainer = new Container<>(Container.TYPE.PERSISTENT, Container.MODEL.EXCLUSIVE);
            service = theContainer.create(new TripImpl(theatre, taxi, altTaxi));
        } else {
            Container<Trip> theContainer = new Container<>(CONTAINER_TYPE, CONTAINER_MODEL);
//            service = theContainer.clone(new TripImpl(theatre, taxi, altTaxi), new Uid(uidName));
            service = theContainer.create(new TripImpl(theatre, taxi, altTaxi));
        }
    }

    protected void initRoutes(Router router) {
        router.route("/api/trip*").handler(BodyHandler.create());

        router.get("/api/trip").handler(this::getAll);
        router.post("/api/trip/:show/:seats/:taxi").handler(this::addWithTaxi);
        router.post("/api/trip/:show/:seats").handler(this::addWithoutTaxi);
    }

    private void addWithoutTaxi(RoutingContext routingContext) {
        String showName =  routingContext.request().getParam("show");
        String seats =  routingContext.request().getParam("seats");
        int noOfSeeats = seats == null ? 1 : Integer.valueOf(seats);

        add(routingContext, showName, noOfSeeats, null);
    }

    private void addWithTaxi(RoutingContext routingContext) {
        String showName =  routingContext.request().getParam("show");
        String seats =  routingContext.request().getParam("seats");
        String taxiName =  routingContext.request().getParam("taxi");
        int noOfSeeats = seats == null ? 1 : Integer.valueOf(seats);

        add(routingContext, showName, noOfSeeats, taxiName);
    }

    private void add(RoutingContext routingContext, String showName, int noOfSeeats, String taxiName) {
        try {
            BookingId theatreBookingId = TheatreVerticle.bookShow(RETRY_COUNT, theatre, showName, noOfSeeats, "TripVerticle");
            List<Booking> bookings = new ArrayList<>();

            bookings.add(theatre.getBooking(theatreBookingId));

            if (taxiName != null) {
                BookingId taxiBookingId;

                try {
                    taxiBookingId = TaxiFirmVerticle.bookTaxi(RETRY_COUNT, taxi, taxiName, noOfSeeats, "TripVerticle");
                    bookings.add(taxi.getBooking(taxiBookingId));
                } catch (BookingException e) {
                    taxiBookingId = TaxiFirmVerticle.bookTaxi(RETRY_COUNT, altTaxi, taxiName, noOfSeeats, "TripVerticle");
                    bookings.add(altTaxi.getBooking(taxiBookingId));
                }
            }

            JsonArray ja = new JsonArray(bookings);

            routingContext.response()
                    .setStatusCode(201)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(ja.encode()));
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
}
