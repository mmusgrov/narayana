package demo1.common.actor;

import org.jboss.stm.annotations.NestedTopLevel;
import org.jboss.stm.annotations.Transactional;

import java.util.Collection;
import java.util.List;

@Transactional
@NestedTopLevel
public interface Trip {
    Collection<BookingId> bookTrip(String showName, int numberOfSeats, int numberOfTaxiSpaces) throws BookingException;

    Booking getBooking(BookingId id) throws BookingException;

    void setCapacity(int capacity);

    void getBookings(List<Booking> bookings);
}
