package demo1;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public class Server extends AbstractVerticle {

/*
      Container<Theatre> theContainer = new Container<Theatre>("Demo", Container.TYPE.PERSISTENT, Container.MODEL.SHARED);
      Theatre obj1 = theContainer.create(new TripImpl(10));

      map.put(LEADER, theContainer.getIdentifier(obj1).toString());


      Container<Theatre> theContainer = new Container<>("Demo", Container.TYPE.PERSISTENT, Container.MODEL.SHARED);
      String uidName = map.get(TripVerticle.LEADER);
      Theatre obj1 = theContainer.clone(new TripImpl(10), new Uid(uidName));

*/

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        DeploymentOptions opts = new DeploymentOptions().setConfig(config()).setInstances(1);
        Future<Void> theatreReady = Future.future();
        Future<Void> taxiReady = Future.future();
        Future<Void> altTaxiReady = Future.future();

        startTheatreVerticle(theatreReady, opts, config().getString("theatre.name"));
        startTaxiVerticle(taxiReady, opts, config().getString("favorite.taxi.name"), config().getInteger("favorite.taxi.http.port", 8083));
        startTaxiVerticle(altTaxiReady, opts, config().getString("alt.taxi.name"), config().getInteger("alt.taxi.http.port", 8084));

        /*
         * wait for the other services to start before starting the trip service
         */
        CompositeFuture.join(theatreReady, taxiReady, altTaxiReady).setHandler(ar -> {
                    if (ar.succeeded()) {
                        startTripVerticle(opts);
                    } else {
                        System.out.printf("Could not start trip service: %s%n", ar.cause().getMessage());
                    }
                }
        );
    }

    private void startTripVerticle(DeploymentOptions opts) {
        TripVerticle verticle = new TripVerticle(config().getString("trip.name"));

        opts.getConfig()
                .put("http.port", opts.getConfig().getInteger("trip.http.port"))
                .put("container.shared", opts.getConfig().getBoolean("trip.container.shared"))
                .put("container.persistent", opts.getConfig().getBoolean("trip.container.persistent"));

        vertx.deployVerticle(verticle, opts);
    }

    private void startTheatreVerticle(Future<Void> future, DeploymentOptions opts, String name) {
        opts.getConfig().put("theatre.name", name).put("http.port", opts.getConfig().getInteger("theatre.http.port"))
                .put("container.shared", opts.getConfig().getBoolean("theatre.container.shared"))
                .put("container.persistent", opts.getConfig().getBoolean("theatre.container.persistent"));

        vertx.deployVerticle(new TheatreVerticle(name), opts, getCompletionHandler(future));
    }

    private void startTaxiVerticle(Future<Void> future, DeploymentOptions opts, String name, int port) {
        opts.getConfig().put("taxi.name", name).put("http.port", port)
                .put("container.shared", opts.getConfig().getBoolean("taxi.container.shared"))
                .put("container.persistent", opts.getConfig().getBoolean("taxi.container.persistent"));

        vertx.deployVerticle(new TaxiVerticle(name), opts, getCompletionHandler(future));
    }

    private Handler<AsyncResult<String>> getCompletionHandler(Future<Void> future) {
        return (AsyncResult<String> res) -> {
            if (res.succeeded())
                future.complete();
            else
                future.fail(res.cause());
        };
    }
}
