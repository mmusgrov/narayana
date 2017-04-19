package demo.trip2.actor;

import demo.common.actor.Trip;
import demo.trip1.TripDemo;
import org.jboss.stm.annotations.NestedTopLevel;
import org.jboss.stm.annotations.Transactional;

/**
 * builds on {@link TripDemo} adding the {@link org.jboss.stm.annotations.NestedTopLevel} annotation.
 * This means that DefaultSTMTrip implementation no longer
 * needs to manage its own transactions. See {@link demo.trip2.internal.RecoverableTripImpl}
 */
@Transactional
@NestedTopLevel
public interface NestedTopLevelSTMTrip extends Trip {
}
