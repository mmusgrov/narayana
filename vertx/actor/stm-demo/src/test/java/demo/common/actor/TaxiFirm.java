package demo.common.actor;

import org.jboss.stm.annotations.NestedTopLevel;
import org.jboss.stm.annotations.Pessimistic;
import org.jboss.stm.annotations.Transactional;

@Transactional
@Pessimistic // if a booking fails there is a good chance that trying again will succeed since there are 2 taxi firms
//@Optimistic
//@Nested
@NestedTopLevel
public interface TaxiFirm {
    String getName();
    BookingId bookTaxi(int numberOfSeats) throws BookingException;
    void changeBooking(BookingId id, int numberOfSeats) throws BookingException;
    int getBookingCount();
    int getCapacity();
    Booking getBooking(BookingId bookingId) throws Exception;
}