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
package demo.trip1;

import demo.common.actor.Trip;
import demo.common.TripDemoCommon;
import demo.trip1.internal.RecoverableTripImpl;

import org.jboss.stm.internal.RecoverableContainer;

public class TripDemo extends TripDemoCommon {
    /**
     * Other demos should implement this method to return a common object appropriate to what the demo is illustrating
     * @param tripContainer an STM container for managing the transactional memory
     * @param capacity
     * @return an proxy whose memory will be managed by the STM container
     */
    protected Trip getTrip(RecoverableContainer<Trip> tripContainer, int capacity) {
        // Create a transactional object
        RecoverableTripImpl tripImpl = new RecoverableTripImpl(capacity);

        /*
         * tripImpl itself is not transactional so we need to pass it to the STM container (of the correct type) and get
         * back a proxy through which the container can monitor access to it and thereby manage its transactional memory:
         */
        return tripContainer.enlist(tripImpl);
    }
}
