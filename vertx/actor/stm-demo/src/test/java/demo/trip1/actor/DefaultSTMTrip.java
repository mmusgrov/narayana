package demo.trip1.actor;

import demo.common.actor.Trip;
import org.jboss.stm.annotations.Transactional;

@Transactional
public interface DefaultSTMTrip extends Trip {
}
