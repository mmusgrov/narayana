package demo1.common.actor;

import org.jboss.stm.annotations.Transactional;

import java.util.List;

@Transactional
//@Pessimistic // if a theatre cannot be booked the who common should be canceled
//@Optimistic // TODO going Optimistic fails
//@Nested
//@NestedTopLevel
public interface Theatre {
    String getName();
    BookingId bookShow(String showName, int numberOfTickets) throws BookingException;
    void changeBooking(BookingId id, int numberOfTickets) throws BookingException;

    Booking getBooking(BookingId bookingId) throws Exception;

    int getBookingCount();
    int getCapacity();

    void getBookings(List<Booking> bookings);
}