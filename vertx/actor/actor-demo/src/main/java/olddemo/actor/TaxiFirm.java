package olddemo.actor;

import org.jboss.stm.annotations.Pessimistic;
import org.jboss.stm.annotations.Transactional;

import java.util.List;

@Transactional
//@Pessimistic // if a booking fails there is a good chance that trying again will succeed since there are 2 taxi firms
@Pessimistic
//@Nested
//@NestedTopLevel
public interface TaxiFirm {
    void initialize();
    String getName();
    BookingId bookTaxi(String reference, int numberOfSeats) throws BookingException;
    void changeBooking(BookingId id, int numberOfSeats) throws BookingException;
    int getBookingCount();
    int getCapacity();
    Booking getBooking(BookingId bookingId) throws Exception;
    void getBookings(List<Booking> bookings);
}