package demo;

import com.arjuna.ats.arjuna.ObjectModel;
import com.arjuna.ats.arjuna.common.Uid;
import demo1.common.actor.Booking;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.jboss.stm.Container;
import org.jboss.stm.internal.PersistentContainer;
import org.jboss.stm.internal.RecoverableContainer;

import java.util.ArrayList;
import java.util.List;

public class BaseVerticle<T> extends AbstractVerticle {
    private Uid uid;
    private String name;
    private LocalMap<String, String> map;

    public BaseVerticle(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public void start(Future<Void> future) {
        map = vertx.sharedData().getLocalMap("demo1.actor.map");

        startServer(future, config().getInteger("http.port", 8080));
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

    RecoverableContainer<T> getRecoverableContainer(String name) {
        boolean isPersistent = config().getBoolean("container.persistent", false);
        boolean isShared = config().getBoolean("container.shared", false);

        if (isPersistent)
            return new PersistentContainer<>(name, isShared ? ObjectModel.SINGLE : ObjectModel.MULTIPLE);

        return new RecoverableContainer<>(name);
    }

    Container<T> getContainer(String name) {
        boolean isPersistent = config().getBoolean("container.persistent", false);
        boolean isShared = config().getBoolean("container.shared", false);
        Container.MODEL model = isShared ? Container.MODEL.SHARED : Container.MODEL.EXCLUSIVE;
        Container.TYPE type = isPersistent ? Container.TYPE.PERSISTENT : Container.TYPE.RECOVERABLE;

        return new Container<>(name, type, model);
    }


    public Uid getUid() {
        return uid;
    }

    String getServiceUid(String name) {
        return map.get(name);
    }

    void advertiseServiceUid(String name, Uid serviceUid) {
        map.put(name, serviceUid.toString());
    }
}
