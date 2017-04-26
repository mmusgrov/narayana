/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package olddemo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.jboss.stm.Container;

public class ServerVerticle extends AbstractVerticle {
    static int RETRY_COUNT = Integer.getInteger("trip.retry.count", 1);
    static boolean HACK = true; // without this hack the theatre and taxi vericles never get the write locks

    static Container.TYPE CONTAINER_TYPE = Container.TYPE.PERSISTENT;
    static Container.MODEL CONTAINER_MODEL = Container.MODEL.SHARED;

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(new ServerVerticle());
    }

    public void start() {
        Vertx vertx = Vertx.vertx();

        Future<Void> theatreReady = Future.future();
        Future<Void> taxiReady = Future.future();
        Future<Void> altTaxiReady = Future.future();

        TheatreVerticle theatreVerticle = new TheatreVerticle("Theatre", TheatreVerticle.DEFAULT_PORT);
        TaxiFirmVerticle taxiFirmVerticle =  new TaxiFirmVerticle("Favorite", TaxiFirmVerticle.DEFAULT_PORT);
        TaxiFirmVerticle altTaxiFirmVerticle = new TaxiFirmVerticle("Alt", TaxiFirmVerticle.DEFAULT_ALT_PORT);

        vertx.deployVerticle(theatreVerticle, getCompletionHandler(theatreReady));
        vertx.deployVerticle(taxiFirmVerticle, getCompletionHandler(taxiReady));
        vertx.deployVerticle(altTaxiFirmVerticle, getCompletionHandler(altTaxiReady));

        CompositeFuture.join(theatreReady, taxiReady, altTaxiReady).setHandler(ar -> {
                    if (ar.succeeded()) {

                        TheatreVerticle.hack(theatreVerticle.getService());
                        TaxiFirmVerticle.hack(taxiFirmVerticle.getService());
                        TaxiFirmVerticle.hack(altTaxiFirmVerticle.getService());

                        vertx.deployVerticle(new TripVerticle("Trip", TripVerticle.DEFAULT_PORT));
//                                theatreVerticle.getService(),
//                                taxiFirmVerticle.getService(),
//                                altTaxiFirmVerticle.getService()));
                    } else {
                        System.out.printf("=== TRIP: Could not start all services: %s%n", ar.cause().getMessage());
                    }
                }
        );
    }

    private Handler<AsyncResult<String>> getCompletionHandler(Future<Void> future) {
        return (AsyncResult<String> res) -> {
            if (res.succeeded()) {
                future.complete();
            } else {
                res.cause().printStackTrace(System.out);
                future.fail(res.cause());
            }
        };
    }
}
