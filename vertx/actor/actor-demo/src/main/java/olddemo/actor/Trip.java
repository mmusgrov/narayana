package olddemo.actor;

import org.jboss.stm.annotations.NestedTopLevel;
import org.jboss.stm.annotations.Pessimistic;
import org.jboss.stm.annotations.Transactional;

import java.util.Collection;
import java.util.List;

@Transactional
@NestedTopLevel
@Pessimistic
public interface Trip {
    Collection<BookingId> bookTrip(String showName, int numberOfSeats, int numberOfTaxiSpaces) throws BookingException;

    Booking getBooking(BookingId id) throws BookingException;

    void getBookings(List<Booking> bookings);
}
