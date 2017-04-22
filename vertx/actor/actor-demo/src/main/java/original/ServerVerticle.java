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

package original;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.Uid;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.LocalMap;
import org.jboss.stm.Container;

public class ServerVerticle extends AbstractVerticle {
    public static String LEADER = "LEADER_SLOT";
    public static Container.TYPE CONTAINER_TYPE = Container.TYPE.PERSISTENT;
    public static Container.MODEL CONTAINER_MODEL = Container.MODEL.SHARED;

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(new ServerVerticle());
    }

    public void start() {
        LocalMap<String, String> map = vertx.sharedData().getLocalMap("olddemo.mymap");

        Container<Sample> theContainer = new Container<>(CONTAINER_TYPE, CONTAINER_MODEL);

        Sample obj1 = theContainer.create(new SampleImple(10));

        assert(obj1 != null);

        map.put(LEADER, theContainer.getIdentifier(obj1).toString());

        vertx.deployVerticle(new SampleVerticle1());

        /*
         * Do some basic checks and ensure state is in store prior to sharing.
         */
        
        AtomicAction A = new AtomicAction();
        
//        A.begin();
        
        obj1.increment();
        obj1.decrement();
        
//        A.commit();
        
        assert(obj1.value() == 10);
        
        assert(theContainer.getIdentifier(obj1).notEquals(Uid.nullUid()));

        Sample obj2 = theContainer.clone(new SampleImple(), theContainer.getIdentifier(obj1));

        assert(obj2 != null);
        
        A = new AtomicAction();
        
//        A.begin();
        
        obj2.increment();
        
//        A.commit();
        
        assert(obj2.value() == 11);
        
        A = new AtomicAction();
        
        A.begin();
        
//        assert(obj1.value() == obj2.value()); the second verticle may have updated the value
        System.out.printf("CCCCCCCCCCCCC: TripVerticleOK: VALUES: obj1=%d obj2=%d%n", obj1.value(), obj2.value());

        A.commit();
    }
}
