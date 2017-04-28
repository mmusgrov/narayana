package demo.verticle;

import demo.stm.Activity;
import demo.stm.TaxiService;
import demo.stm.TaxiServiceImpl;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.jboss.stm.Container;

public class TaxiVolatileVerticle extends BaseVerticle {
    static Container<TaxiService> container;
    static TaxiService service;

    public static void main(String[] args) {
        container = new Container<>(Container.TYPE.RECOVERABLE, Container.MODEL.EXCLUSIVE);

        parseArgs(args);

        if (uid == null) {
            service = container.create(new TaxiServiceImpl());
            uid = container.getIdentifier(service);
            System.out.printf("CREATED uid=%s%n", uid == null ? "null" : uid.toString());
            initializeSTMObject(service);
        }

        deployVerticle(TaxiVolatileVerticle.class.getName(), "taxi");
    }

    Activity initService() {
        assert service != null;

        return container.clone(new TaxiServiceImpl(), service);
    }
    void initRoutes(Router router) {
        super.initRoutes(router);

        router.post("/api/" + getServiceName() + "/:seats").handler(this::bookTaxi);
        router.get("/api/" + getServiceName() + "/book/:seats").handler(this::bookTaxi2);
        router.get("/api/" + getServiceName()).handler(this::listBookings);

    }

    private void listBookings(RoutingContext routingContext) {
        System.out.printf("XXX listing taxi bookings%n");

        super.performActivity(routingContext);
    }

    private void bookTaxi(RoutingContext routingContext) {
        System.out.printf("listing taxi bookings%n");

        super.performActivity(routingContext);
    }

    private void bookTaxi2(RoutingContext routingContext) {
        System.out.printf("booking a taxi%n");

        super.performActivity(routingContext);
    }
}
