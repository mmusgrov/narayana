package demo1;

import com.arjuna.ats.arjuna.common.Uid;
import demo1.common.actor.Booking;
import demo1.common.actor.BookingException;
import demo1.common.actor.BookingId;
import demo1.common.actor.Theatre;
import demo1.common.internal.TheatreImpl;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.jboss.stm.Container;

import java.util.List;

public class TheatreVerticle extends BaseVerticle {
    private Theatre service;
    private Uid serviceUid;

    public TheatreVerticle(String name) {
        super(name);
    }

    protected void initRoutes(Router router) {
        router.route("/api/theatre*").handler(BodyHandler.create());

        router.get("/api/theatre").handler(this::getAll);
        router.post("/api/theatre/:show/:seats").handler(this::addOne);
    }

    private void addOne(RoutingContext routingContext) {
        String showName =  routingContext.request().getParam("show");
        String seats =  routingContext.request().getParam("seats");
        int noOfSeeats = seats == null ? 1 : Integer.valueOf(seats);

        try {
            BookingId id = service.bookShow(showName, noOfSeeats);
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

    protected void initServices() {
        int capacity = Integer.getInteger("theatre.capacity", 50);
//        Container<Theatre> container = getContainer("Theatre");
       Container<Theatre> container = new Container<>("Theatre", Container.TYPE.PERSISTENT, Container.MODEL.SHARED);
        TheatreImpl theatreImpl = new TheatreImpl(getName(), capacity);

        service = container.create(theatreImpl);
        serviceUid = container.getIdentifier(service);
        theatreImpl.setUid(serviceUid.toString());
        advertiseServiceUid(getName(), serviceUid);
    }

    @Override
    public Uid getUid() {
        return serviceUid;
    }
}
