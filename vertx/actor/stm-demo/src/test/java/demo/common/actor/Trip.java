package demo.common.actor;

import java.util.Collection;

public interface Trip {
    Collection<BookingId> bookTrip(String showName, int numberOfSeats, int numberOfTaxiSpaces) throws BookingException;

    Booking getBooking(BookingId id) throws BookingException;

    void setCapacity(int capacity);
}
