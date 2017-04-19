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
package demo.trip4;

import demo.common.actor.Booking;
import demo.common.actor.Trip;
import demo.trip4.internal.RecoverableTripImpl;
import org.jboss.stm.internal.RecoverableContainer;

import java.util.Collection;

/**
 * Similar to {@link demo.trip1.TripDemo} but uses conventional concurrency primitives in favour of STM
 */
public class TripDemo extends demo.trip1.TripDemo {
    protected Trip getTrip(RecoverableContainer<Trip> tripContainer, int capacity) {
        return new RecoverableTripImpl(capacity); // common is no longer managed by the STM container
    }

    /**
     * a synchronized version of bookTrip since there is no STM support in this example
     *
     * @param trip
     * @param bookings
     * @param showName
     * @param numberOfSeats
     * @param numberOfTaxSpaces
     */
    protected synchronized void bookTrip(Trip trip, Collection<Booking> bookings, String showName, int numberOfSeats, int numberOfTaxSpaces) {
        super.bookTrip(trip, bookings, showName, numberOfSeats, numberOfTaxSpaces);
    }
}
