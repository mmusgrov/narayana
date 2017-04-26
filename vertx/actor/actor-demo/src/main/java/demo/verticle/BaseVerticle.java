package demo.verticle;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.Uid;
import demo.stm.Activity;
import demo.stm.Theatre;
import demo.stm.TheatreImpl;
import demo.stm.TaxiService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.jboss.stm.Container;

public abstract class BaseVerticle<T> extends AbstractVerticle {
    private static int numberOfServiceInstances = 1;
    private static int httpPort = 8080;
    private static Uid uid = null;

    private static Theatre initialSTMObject;
    private static Container<Theatre> theContainer;

    private Theatre mandatory;
    private TaxiService optional;

    String getName() {
        return getClass().getName();
    }

    private static void parseArgs(String[] args) {
        httpPort = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        numberOfServiceInstances = args.length == 1 ? 1 : Integer.parseInt(args[1]);
        uid = args.length == 2 ? null : new Uid(args[2]);

        if (httpPort <= 0 || numberOfServiceInstances <= 0)
            throw new IllegalArgumentException("syntax: instance count and http port must be greater than zero%n");

        System.out.printf("Running %d vertx event listeners on http port %d%n", numberOfServiceInstances, httpPort);
    }

    static void deployVerticle(String verticleClassName, Container container, String[] args) {
        parseArgs(args);

        Vertx vertx = Vertx.vertx();

        theContainer = container;

        initialSTMObject = uid == null ?
                theContainer.create(new TheatreImpl()) :
                theContainer.clone(new TheatreImpl(), uid);

        uid = container.getIdentifier(initialSTMObject);

        System.out.printf("Created an STM ojbect with id: %s%n", uid.toString());

        // workaround for JBTM-1732
        initializeSTMObject(initialSTMObject);

        DeploymentOptions opts = new DeploymentOptions().
                setInstances(numberOfServiceInstances).
                setConfig(new JsonObject().put("name", "Volatile Verticle").put("port", httpPort));

        vertx.deployVerticle(verticleClassName, opts);
    }

    @Override
    public void start(Future<Void> future) {
        int listenerPort = config().getInteger("port", 8080);

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

    private void initRoutes(Router router) {
        router.route("/api/activity*").handler(BodyHandler.create());

        router.get("/api/activity/uid").handler(this::getUid);
        router.get("/api/activity").handler(this::getActivity);

        router.post("/api/activity/:name").handler(this::performActivity);
    }

    private void initServices() {
        // get a handle to an STM object for performing optional actions
//        optional = new Container<TaxiService>().create(new TaxiServiceImpl()); // TaxiService is NestedTopLevel

        // get a handle to an STM object for performing mandatory actions
        if (uid == null) {
            mandatory = theContainer.create(new TheatreImpl()); // Theatre is Nested
            uid = theContainer.getIdentifier(mandatory);
            System.out.printf("CREATED uid=%s%n", uid == null ? "null" : uid.toString());
        } else {
            mandatory = theContainer.clone(new TheatreImpl(), initialSTMObject); // Theatre is Nested
            System.out.printf("CLONED using uid %s%n", uid.toString());
        }
    }

    private void getUid(RoutingContext routingContext) {
        routingContext.response()
                .setStatusCode(201)
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(Json.encodePrettily(uid.toString()));
    }

    private void getActivity(RoutingContext routingContext) {
        try {
            AtomicAction A = new AtomicAction();

            A.begin();
            int activityCount = mandatory.getValue(); // done as a sub transaction of A since mandatory is annotated wiht @Nested
            A.commit();

            routingContext.response()
                    .setStatusCode(201)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(new ServiceResult(getName(), Thread.currentThread().getName(), activityCount)));
        } catch (Exception e) {
            routingContext.response()
                    .setStatusCode(406)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(new JsonObject().put("Status", e.getMessage()).encode());
        }
    }

    private void performActivity(RoutingContext routingContext) {
        try {
            AtomicAction A = new AtomicAction();

            A.begin();
            mandatory.activity(); // done as a sub transaction of A since mandatory is annotated wiht @Nested
            int activityCount = mandatory.getValue();
            A.commit();

            routingContext.response()
                    .setStatusCode(201)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(new ServiceResult(getName(), Thread.currentThread().getName(), activityCount)));
        } catch (Exception e) {
            routingContext.response()
                    .setStatusCode(406)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(new JsonObject().put("Status", e.getMessage()).encode());
        }
    }

    // workaround for JBTM-1732
    private static void initializeSTMObject(Theatre activity) {
        AtomicAction A = new AtomicAction();

        A.begin();
        activity.init();
        A.commit();
    }
}
