package demo.common.actor;

import org.jboss.stm.annotations.NestedTopLevel;
import org.jboss.stm.annotations.Pessimistic;
import org.jboss.stm.annotations.Transactional;

@Transactional
@Pessimistic // if a theatre cannot be booked the who common should be canceled
//@Optimistic
//@Nested
@NestedTopLevel
public interface Theatre {
    String getName();
    BookingId bookShow(String showName, int numberOfTickets) throws BookingException;
    void changeBooking(BookingId id, int numberOfTickets) throws BookingException;

    Booking getBooking(BookingId bookingId) throws Exception;

    int getBookingCount();
    int getCapacity();
}