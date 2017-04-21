package demo1;

import com.arjuna.ats.arjuna.common.Uid;
import com.sun.javafx.binding.StringFormatter;
import demo1.common.actor.Booking;
import demo1.common.actor.BookingException;
import demo1.common.actor.BookingId;
import demo1.common.actor.TaxiFirm;
import demo1.common.actor.Theatre;
import demo1.common.actor.Trip;
import demo1.common.internal.TaxiFirmImpl;
import demo1.common.internal.TheatreImpl;
import demo1.common.internal.TripImpl;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.jboss.stm.Container;
import org.jboss.stm.internal.RecoverableContainer;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TripVerticle extends BaseVerticle {
    private Trip tripService = null;
    private String theatreName;
    private String taxiName;
    private String altTaxiName;

    public TripVerticle(String name) {
        super(name);
    }

    protected void initRoutes(Router router) {
        router.route("/api/trip*").handler(BodyHandler.create());

        router.get("/api/trip").handler(this::getAll);
        router.post("/api/trip/:show/:seats").handler(this::addTrip);
    }

    protected void initServices() {
        tripService = getTripService();
    }

    private Theatre createTheatreProxy(Container<Theatre> theatreContainer, String theatreName, int theatreCapacity, String theatreUid) {
        Theatre theatreImpl = new TheatreImpl(theatreUid, theatreName, theatreCapacity);

        if (theatreUid != null) {
            return theatreContainer.clone(theatreImpl, new Uid(theatreUid));
        } else {
            return theatreContainer.create(theatreImpl);
        }
    }

    private TaxiFirm createTaxiProxy(Container<TaxiFirm> taxiContainer, String taxiName, int taxiCapacity, String taxiUid) {
        TaxiFirm taxiProxy;
        TaxiFirmImpl taxiImpl = new TaxiFirmImpl(taxiUid, taxiName, taxiCapacity);

        if (taxiUid != null) {
            taxiProxy = taxiContainer.clone(taxiImpl, new Uid(taxiUid));
        } else {
            taxiProxy = taxiContainer.create(taxiImpl);
        }

        return taxiProxy;
    }

    private Trip getTripService() {
        RecoverableContainer<Trip> tripContainer = new RecoverableContainer<>("Trip"); //getContainer("Trip");

//        Container<Theatre> theatreContainer = getContainer("Theatre");
//        Container<TaxiFirm> taxiContainer = getContainer("Taxi");

        Container<Theatre> theatreContainer = new Container<>("Theatre", Container.TYPE.PERSISTENT, Container.MODEL.SHARED);
        Container<TaxiFirm> taxiContainer = new Container<>("TaxiFirm", Container.TYPE.PERSISTENT, Container.MODEL.SHARED);

        int theatreCapacity = config().getInteger("theatre.capacity");
        int taxiCapacity = config().getInteger("favorite.taxi.capacity");
        int altTaxiCapacity = config().getInteger("alt.taxi.capacity");

        theatreName = config().getString("theatre.name", "Theatre");
        taxiName = config().getString("favorite.taxi.name", "Favorite");
        altTaxiName = config().getString("alt.taxi.name", "Alternate");

        String theatreUid = getServiceUid("Theatre");
        String taxiUid = getServiceUid("favorite.taxi.name");
        String altTaxiUid = getServiceUid("alt.taxi.name");

        Theatre theatreProxy = createTheatreProxy(theatreContainer, theatreName, theatreCapacity, theatreUid);
        TaxiFirm taxiProxy = createTaxiProxy(taxiContainer, taxiName, taxiCapacity, taxiUid);
        TaxiFirm altTaxiProxy = createTaxiProxy(taxiContainer, altTaxiName, altTaxiCapacity, altTaxiUid);

        TripImpl tripImpl = new TripImpl(theatreProxy, taxiProxy, altTaxiProxy);

        /*
         * tripImpl itself is not transactional so we need to pass it to the STM container (of the correct type) and get
         * back a proxy through which the container can monitor access to it and thereby manage its transactional memory:
         */
        return tripContainer.enlist(tripImpl);
    }

    private void addTrip(RoutingContext routingContext) {
        String showName =  routingContext.request().getParam("show");
        String seats =  routingContext.request().getParam("seats");
        int noOfSeeats = seats == null ? 1 : Integer.valueOf(seats);

        List<Booking> bookings = new ArrayList<>();

        bookTrip(tripService, bookings, showName, noOfSeeats, noOfSeeats);

        JsonArray ja = new JsonArray(bookings);

        // Return the created trip as JSON
        routingContext.response()
                .setStatusCode(201)
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(ja.encode());
/*                        new JsonObject().put("name", showName)
                                .put("seats", seats)
                                .encode());*/
    }


    protected Booking getBooking(Trip trip, BookingId id) {
        try {
            return trip.getBooking(id);
        } catch (BookingException e) {
            System.out.printf("booking id %s not found%n", id.toString());
            return null;
        }
    }

    protected void bookTrip(Trip trip, Collection<Booking> bookings, String showName, int numberOfSeats, int numberOfTaxSpaces) {
        try {
            trip.bookTrip(showName, numberOfSeats, numberOfTaxSpaces).forEach(id -> {
                Booking booking = getBooking(trip, id);

                if (booking != null)
                    bookings.add(booking);
            });
        } catch (BookingException e) {
            System.out.printf("Booking failed: %s%n", e.getMessage());
        }
    }

    protected void getAll(RoutingContext routingContext) {
        List<Booking> bookings = new ArrayList<>();

        tripService.getBookings(bookings);

        System.out.printf("%s bookings:%n", "Summary:");

        Map<String, Integer> map =
                bookings.stream()
                        .flatMap(i -> Stream.of(new AbstractMap.SimpleEntry<>(i.getName(), i.getSize())))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum));

//        map.forEach((k,v) -> System.out.printf("%s: %d%n", k, v));

        int showBookings = map.computeIfAbsent(theatreName, v -> 0);
        int favTaxiBookings = map.computeIfAbsent(taxiName, v -> 0);
        int altTaxiBookings = map.computeIfAbsent(altTaxiName, v -> 0);

        String result = StringFormatter.format("RESULT: %d vrs %d + %d%n", showBookings, favTaxiBookings, altTaxiBookings).get();
        //       System.out.printf("RESULT: %d vrs %d + %d%n", showBookings, favTaxiBookings, altTaxiBookings);

        // Write the HTTP response
        // The response is in JSON using the utf-8 encoding
        // We returns the list of bottles
        routingContext.response()
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(result);
//                .end(Json.encodePrettily(bookings));
    }
}
