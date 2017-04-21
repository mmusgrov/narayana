package demo1.trip2.actor;

import demo1.common.actor.Trip;
import org.jboss.stm.annotations.NestedTopLevel;
import org.jboss.stm.annotations.Transactional;

/**
 * builds on {@link TripDemo} adding the {@link NestedTopLevel} annotation.
 * This means that DefaultSTMTrip implementation no longer
 * needs to manage its own transactions. See {@link demo1.trip2.internal.RecoverableTripImpl}
 */
@Transactional
@NestedTopLevel
public interface NestedTopLevelSTMTrip extends Trip {
}
