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

package demo.common;

import com.arjuna.ats.arjuna.ObjectModel;
import demo.common.actor.Booking;
import demo.common.actor.BookingException;
import demo.common.actor.BookingId;
import demo.common.actor.Trip;
import demo.trip1.internal.RecoverableTripImpl;
import org.jboss.stm.internal.PersistentContainer;
import org.jboss.stm.internal.RecoverableContainer;
import org.junit.Test;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public abstract class TripDemoCommon {
    /**
     * Demos should implement this method to return a common object appropriate to what the demo is illustrating
     * @param tripContainer an STM container for managing the transactional memory
     * @param capacity
     * @return an proxy whose memory will be managed by the STM container
     */
    abstract protected Trip getTrip(RecoverableContainer<Trip> tripContainer, int capacity);

    /**
     * Demos should override this method if required
     *
     * @param trip the interface to use to obtain the booking
     * @param id the id for which a booking is being sought
     * @return the booking if it exists or null otherwise
     */
    protected Booking getBooking(Trip trip, BookingId id) {
        try {
            return trip.getBooking(id);
        } catch (BookingException e) {
            System.out.printf("booking id %s not found%n", id.toString());
            return null;
        }
    }

    @Test
    public void testRecoverableTrip() throws BookingException {
        testTripCommon("testRecoverableTrip", new RecoverableContainer<>());
    }

    @Test
    public void testPersistentTrip () throws BookingException
    {
        testTripCommon("testPersistentTrip", new PersistentContainer<>());
    }

    @Test
    public void testPersistentTripMULTIPLE () throws BookingException
    {
        testTripCommon("testPersistentTripMULTIPLE", new PersistentContainer<>(ObjectModel.MULTIPLE));
    }

    /**
     * when booking a trip the bookings are stored in a standard map {@link demo.common.internal.TripPart.bookings}
     * and unless suitable concurrency control methods are applied that map can be corrupted. The default implementation
     * assumes that the implementation of each service is protected by some mechanism (STM, java.util.concurrency, actors, etc)
     */
    protected void bookTrip(Trip trip, Collection<Booking> bookings, String showName, int numberOfSeats, int numberOfTaxSpaces) {
        try {
            trip.bookTrip(showName, numberOfSeats, numberOfTaxSpaces).forEach(id -> {
                Booking booking = getBooking(trip, id);

                if (booking != null)
                    bookings.add(booking);
            });
        } catch (BookingException e) {
            System.out.printf("Booking failed: %s%n", e.getMessage());
        }
    }

    /**
     * @param testName the name of the test for reporting results
     * @param tripContainer an STM Container for managing the transactional memory associated with transactional objects
     *                      The generic type indicates which type of objects will be managed
     */
    private void testTripCommon(String testName, RecoverableContainer<Trip> tripContainer) {
        int capacity = Integer.getInteger("thread.count", 1);
        Trip trip = getTrip(tripContainer, capacity);

        List<Booking> bookings = new ArrayList<>();

        // book multiple trips in parallel
        IntStream.range(0, capacity).parallel().forEach(i -> {
            bookTrip(trip, bookings, "Cats", 2, 2);
        });

        // calculate how many bookings there are for each service
        System.out.printf("%s bookings:%n", testName);

        Map<String, Integer> map =
                bookings.stream()
                        .flatMap(i -> Stream.of(new SimpleEntry<>(i.getName(), i.getSize())))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum));

        map.forEach((k,v) -> System.out.printf("%s: %d%n", k, v));

        int showBookings = map.computeIfAbsent(RecoverableTripImpl.SHOW_NAME, v -> 0);
        int favTaxiBookings = map.computeIfAbsent(RecoverableTripImpl.FAVORITE_TAXI_NAME, v -> 0);
        int altTaxiBookings = map.computeIfAbsent(RecoverableTripImpl.RIVAL_TAXI_NAME, v -> 0);

        System.out.printf("RESULT: %d vrs %d + %d%n", showBookings, favTaxiBookings, altTaxiBookings);
        assertEquals("Number of show bookings does not equal number of taxi bookings",
                showBookings, favTaxiBookings + altTaxiBookings);
    }
}
