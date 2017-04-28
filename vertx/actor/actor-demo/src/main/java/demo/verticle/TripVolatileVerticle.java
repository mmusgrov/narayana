package demo.verticle;

import demo.stm.Activity;
import demo.stm.Theatre;

import demo.stm.TheatreImpl;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.jboss.stm.Container;

public class TripVolatileVerticle extends BaseVerticle {
    static Container<Theatre> container;
    static Theatre service;
    static int taxiServicePort;

    HttpClient taxiClient;

    public static void main(String[] args) {
        container = new Container<>(Container.TYPE.RECOVERABLE, Container.MODEL.EXCLUSIVE);

        parseArgs(args);

        taxiServicePort = getIntOption("taxi.port", 8080);

        if (uid == null) {
            service = container.create(new TheatreImpl()); // Theatre is Nested
            uid = container.getIdentifier(service);
            System.out.printf("CREATED uid=%s%n", uid == null ? "null" : uid.toString());
        } else {
            service = container.clone(new TheatreImpl(), uid);
            System.out.printf("CLONED uid=%s%n", uid == null ? "null" : uid.toString());
        }

        initializeSTMObject(service);

        deployVerticle(TripVolatileVerticle.class.getName(), "theatre");
    }

    Activity initService() {
        assert service != null;

        taxiClient = vertx.createHttpClient();

        return container.clone(new TheatreImpl(), service); // Theatre is Nested
    }

    void initRoutes(Router router) {
        router.post("/api/" + getServiceName() + "/:name/:taxi").handler(this::bookTrip);

        super.initRoutes(router);
    }

    private void bookTrip(RoutingContext routingContext) {
        TripHelper.bookTrip(taxiClient, routingContext, service, getServiceName(), taxiServicePort);
    }

    private void bookTaxi(String taxiName) {
        TripHelper.bookTaxi(taxiClient, taxiServicePort);
    }
}
