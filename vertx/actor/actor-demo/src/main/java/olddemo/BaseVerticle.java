package olddemo;

import com.arjuna.ats.arjuna.common.Uid;
import olddemo.actor.Booking;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;

public class BaseVerticle<T> extends AbstractVerticle {
    private String name;
    private int listenerPort = 8080;
    private LocalMap<String, String> map;

    public BaseVerticle(String name, int listenerPort) {
        this.name = name;
        this.listenerPort = listenerPort;
    }

    public String getName() {
        return name;
    }

    @Override
    public void start(Future<Void> future) {
        map = vertx.sharedData().getLocalMap("olddemo.actor.map");

        startServer(future, listenerPort);
    }

    private void startServer(Future<Void> future, int listenerPort) {
        Router router = Router.router(vertx);

        initServices();
        initRoutes(router);

        // Create the HTTP server and pass the "accept" method to the request handler.
        vertx
                .createHttpServer()
                .requestHandler(router::accept)
                .listen(listenerPort,
                        result -> {
                            if (result.succeeded()) {
                                future.complete(); // tell the caller the server is ready
                            } else {
                                result.cause().printStackTrace(System.out);
                                future.fail(result.cause()); // tell the caller that server failed to start
                            }
                        }
                );
    }

    protected void initRoutes(Router router) {
    }

    protected void initServices() {
    }

    protected void getAll(RoutingContext routingContext) {
        List<Booking> bookings = new ArrayList<>();

        getBookings(bookings);

        JsonArray ja = new JsonArray(bookings);

        routingContext.response()
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(ja.encode());
    }

    void getBookings(List<Booking> bookings) {
    }

    // persistent and shared: valid combination
    // persistent and exclusive: valid combination
    // recoverable and exclusive: valid combination
    // recoverable and shared: invalid combination
    static boolean isExclusive(String name) {
        return Boolean.getBoolean(name.toLowerCase() + ".exclusive");
    }

    boolean isExclusive() {
        return isExclusive(name);
    }

    String getServiceUid(String name) {
        return map.get(name);
    }

    void advertiseServiceUid(String name, Uid serviceUid) {
        map.put(name, serviceUid.toString());
    }
}
