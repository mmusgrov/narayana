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

package demo;

import demo.actor.Booking;
import demo.actor.TaxiFirm;
import demo.actor.Theatre;
import demo.internal.TaxiFirmImpl;
import demo.internal.TheatreImpl;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.LocalMap;
import org.jboss.stm.Container;

import java.util.ArrayList;
import java.util.List;

public class TripVerticle extends AbstractVerticle {
    static String THEATRE_SLOT = "THEATRE_SLOT";
    static String TAXI_SLOT = "TAXI_SLOT";

    static int RETRY_COUNT = Integer.getInteger("trip.retry.count", 1);
    static boolean HACK = true; // without this hack the theatre and taxi vericles never get the write locks

    static Container.TYPE CONTAINER_TYPE = Container.TYPE.PERSISTENT;
    static Container.MODEL CONTAINER_MODEL = Container.MODEL.SHARED;

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(new TripVerticle());
    }

    public void start() {
        LocalMap<String, String> map = vertx.sharedData().getLocalMap("demo1.mymap");

        Container<Theatre> theatreContainer = new Container<>(CONTAINER_TYPE, CONTAINER_MODEL);
        Container<TaxiFirm> taxiContainer = new Container<>(CONTAINER_TYPE, CONTAINER_MODEL);

        Theatre theatre = theatreContainer.create(new TheatreImpl("Theatre", 50));
        TaxiFirm taxi = taxiContainer.create(new TaxiFirmImpl("Favorite", 40));

        assert(theatre != null);
        assert(taxi != null);

        /*
         * without the following hack the theatre and taxi vericles never get the write locks
         * because the object store hierarchy never gets created and attempts to get the lock fail if the
         * lock hierarchy does not exist
         */
        TheatreVerticle.hack(theatre);
        TaxiVerticle.hack(taxi);

        map.put(THEATRE_SLOT, theatreContainer.getIdentifier(theatre).toString());
        map.put(TAXI_SLOT, taxiContainer.getIdentifier(taxi).toString());

        Future<Void> theatreReady = Future.future();
        Future<Void> taxiReady = Future.future();

        vertx.deployVerticle(new TheatreVerticle(), getCompletionHandler(theatreReady));
        vertx.deployVerticle(new TaxiVerticle(), getCompletionHandler(taxiReady));

        CompositeFuture.join(theatreReady, taxiReady).setHandler(ar -> {
                    if (ar.succeeded()) {
                        bookTrip(theatre, taxi);
                        listBookings(theatre, taxi, "=== TRIP");
                    } else {
                        System.out.printf("=== TRIP: Could not start all services: %s%n", ar.cause().getMessage());
                    }
                }
        );
    }

    private void bookTrip(Theatre theatre, TaxiFirm taxi) {
        TheatreVerticle.bookShow(RETRY_COUNT, theatre, "Evita", 4, "TripVerticle");
        TaxiVerticle.bookTaxi(RETRY_COUNT, taxi, 2, "TripVerticle");
    }

    private void listBookings(Theatre theatre, TaxiFirm taxi, String debugMsg) {
        System.out.printf("%s: Bookings:%n", debugMsg);

        List<Booking> bookings = TheatreVerticle.getBookings(RETRY_COUNT, theatre, new ArrayList<>(), "TripVerticle");
        TaxiVerticle.getBookings(RETRY_COUNT, taxi, bookings, "TripVerticle");

        bookings.forEach(booking ->
                System.out.printf("\t%s booking for %s for %d%n",
                    booking.getName(), booking.getDescription(), booking.getSize()));
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
