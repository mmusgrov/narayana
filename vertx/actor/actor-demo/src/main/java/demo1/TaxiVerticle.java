package demo1;

import com.arjuna.ats.arjuna.common.Uid;
import demo1.common.actor.Booking;
import demo1.common.actor.BookingException;
import demo1.common.actor.BookingId;
import demo1.common.actor.TaxiFirm;
import demo1.common.internal.TaxiFirmImpl;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.jboss.stm.Container;

import java.util.List;

public class TaxiVerticle extends BaseVerticle {
    private TaxiFirm service;
    private Uid serviceUid;

    public TaxiVerticle(String name) {
        super(name);
    }

    protected void initRoutes(Router router) {
        String apiBase = "/api/taxi/" + getName();

        router.route(apiBase + "*").handler(BodyHandler.create());

        router.get(apiBase).handler(this::getAll);
        router.post(apiBase + "/:seats").handler(this::addOne);
    }

    private void addOne(RoutingContext routingContext) {
        String seats =  routingContext.request().getParam("seats");
        int noOfSeeats = seats == null ? 1 : Integer.valueOf(seats);

        try {
            BookingId id = service.bookTaxi(noOfSeeats);
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

    @Override
    void getBookings(List list) {
        super.getBookings(list);
    }

    protected void initServices() {
        int capacity = Integer.getInteger(getName() + ".taxi.capacity", 4);
//        Container<TaxiFirm> container = getContainer(getName());
        Container<TaxiFirm> container = new Container<>("TaxiFirm", Container.TYPE.PERSISTENT, Container.MODEL.SHARED);
        TaxiFirmImpl taxiImpl = new TaxiFirmImpl(getName(), capacity);

        service = container.create(taxiImpl);
        serviceUid = container.getIdentifier(service);
        taxiImpl.setUid(serviceUid.toString());
        advertiseServiceUid(getName(), serviceUid);
    }

    @Override
    public Uid getUid() {
        return serviceUid;
    }
}
