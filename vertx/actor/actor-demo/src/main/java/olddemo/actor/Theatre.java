package olddemo.actor;

import org.jboss.stm.annotations.Nested;
import org.jboss.stm.annotations.Pessimistic;
import org.jboss.stm.annotations.Transactional;

import java.util.List;

@Transactional
//@Pessimistic // if a theatre cannot be booked the who common should be canceled
@Pessimistic
@Nested
//@NestedTopLevel
public interface Theatre {
    void initialize();
    String getName();
    BookingId bookShow(String showName, int numberOfTickets) throws BookingException;
    void changeBooking(BookingId id, int numberOfTickets) throws BookingException;

    Booking getBooking(BookingId bookingId) throws BookingException;

    int getBookingCount();
    int getCapacity();

    void getBookings(List<Booking> bookings);

/*    // for testing sharing objects
    void increment ();
    void decrement ();

    int value ();*/
}