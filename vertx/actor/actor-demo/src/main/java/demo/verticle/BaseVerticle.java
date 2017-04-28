package demo.verticle;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.Uid;
import demo.stm.Activity;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseVerticle<T extends Activity> extends AbstractVerticle {
    private static int numberOfServiceInstances = 1;
    private static int httpPort = 8080;

    static Uid uid = null;

    static String xarg = null;
    static Map<String, String> options;

    private Activity mandatory;
    private TaxiService optional;
    private String serviceName;

    String getServiceName() {
        return serviceName;
    }

    static void addOption(String opt) {
        if (opt != null && opt.contains("=")) {
            String [] pair = opt.split("=");

            options.put(pair[0], pair[1]);
        }
    }

    static int getIntOption(String optionName, int defaultValue) {
        return options.containsKey(optionName) ?
            Integer.parseInt(options.get(optionName)) :
                defaultValue;
    }

    static String getStringOption(String optionName, String defaultValue) {
        return options.containsKey(optionName) ?
                options.get(optionName) :
                defaultValue;
    }

    static void parseArgs(String[] args) {
        options = new HashMap<>();

        Arrays.stream(args).forEach(BaseVerticle::addOption);

        httpPort = getIntOption("port", 8080);
        numberOfServiceInstances = getIntOption("count", 1);
        String uidStr = getStringOption("uid", null);

        if (uidStr != null)
            uid = new Uid(uidStr);

        if (httpPort <= 0 || numberOfServiceInstances <= 0)
            throw new IllegalArgumentException("syntax: instance count and http port must be greater than zero%n");


        System.out.printf("Running %d vertx event listeners on http port %d%n", numberOfServiceInstances, httpPort);
    }

    static void deployVerticle(String verticleClassName, String verticleName) {

        Vertx vertx = Vertx.vertx();

        DeploymentOptions opts = new DeploymentOptions().
                setInstances(numberOfServiceInstances).
                setConfig(new JsonObject().put("name", verticleName).put("port", httpPort));

        vertx.deployVerticle(verticleClassName, opts);
    }

    @Override
    public void start(Future<Void> future) {
        int listenerPort = config().getInteger("port", 8080);

        serviceName = config().getString("name", "activity");
        mandatory = initService();

        startServer(future, listenerPort);
    }

    private void startServer(Future<Void> future, int listenerPort) {
        Router router = Router.router(vertx);

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

        assert router.getRoutes().size() > 0;

        String route1 = router.getRoutes().get(0).getPath();

        System.out.printf("%s service listening on http://localhost:%d%s%n" , getServiceName(), listenerPort, route1);
    }

    void initRoutes(Router router) {
        router.route("/api/" + getServiceName() + "*").handler(BodyHandler.create());

        router.get("/api/" + getServiceName() + "/uid").handler(this::getUid);
        router.get("/api/" + getServiceName()).handler(this::getActivity);

        router.post("/api/" + getServiceName() + "/:name").handler(this::performActivity);
    }


    abstract Activity initService();

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

            System.out.printf("%s: cnt: %d%n", getServiceName(), mandatory.getValue());
            routingContext.response()
                    .setStatusCode(201)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(new ServiceResult(getServiceName(), Thread.currentThread().getName(), activityCount)));
        } catch (Exception e) {
            routingContext.response()
                    .setStatusCode(406)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(new JsonObject().put("Status", e.getMessage()).encode());
        }
    }

    void performActivity(RoutingContext routingContext) {
        try {
            AtomicAction A = new AtomicAction();

            A.begin();
            mandatory.activity(); // done as a sub transaction of A since mandatory is annotated wiht @Nested
            int activityCount = mandatory.getValue();
            A.commit();

            routingContext.response()
                    .setStatusCode(201)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(new ServiceResult(getServiceName(), Thread.currentThread().getName(), activityCount)));
        } catch (Exception e) {
            routingContext.response()
                    .setStatusCode(406)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(new JsonObject().put("Status", e.getMessage()).encode());
        }
    }

    // workaround for JBTM-1732
    static void initializeSTMObject(Activity activity) {
        AtomicAction A = new AtomicAction();

        A.begin();
        activity.init();
        A.commit();
    }
}
