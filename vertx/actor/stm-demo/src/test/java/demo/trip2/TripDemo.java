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
package demo.trip2;

import demo.common.actor.Trip;
import demo.trip2.internal.RecoverableTripImpl;

import org.jboss.stm.internal.RecoverableContainer;

public class TripDemo extends demo.trip1.TripDemo {
    protected Trip getTrip(RecoverableContainer<Trip> tripContainer, int capacity) {
        /*
         * create a transactional object that does not need to manage its own transactions (because
         * demo.trip2.RecoverableTripImpl is annotated with {@link org.jboss.stm.annotations.NestedTopLevel}
         */
        RecoverableTripImpl tripImpl = new RecoverableTripImpl(capacity);

        /*
         * tripImpl itself is not transactional so we need to pass it to the STM container (of the correct type) and get
         * back a proxy through which the container can monitor access to it and thereby manage its transactional memory:
         */
        return tripContainer.enlist(tripImpl);
    }
}
