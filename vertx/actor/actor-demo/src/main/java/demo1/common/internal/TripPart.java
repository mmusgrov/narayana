package demo1.common.internal;

import demo1.common.actor.Booking;
import demo1.common.actor.BookingException;
import demo1.common.actor.BookingId;
import org.jboss.stm.annotations.NotState;
import org.jboss.stm.annotations.State;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TripPart {
    @State
    private String name;
    @State
    private int capacity;
    @State
    private Map<BookingId, Booking> bookings;
    @State
    private int size;
    @NotState
    private String uid; // TODO for debug purposes

    TripPart(String uid, String name, int capacity) {
        this.uid = uid;
        this.name = name;
        this.capacity = capacity;
        this.bookings = new HashMap<>(capacity);
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    String getName() {
        return name;
    }

    int getBookingCount() {
        return size;
    }

    int getCapacity() {
        return capacity;
    }

    BookingId book(int numberRequired) throws BookingException {
        if (numberRequired <= 0)
            throw new BookingException("booking sizes should be greater than zero");

        if (size + numberRequired > capacity)
            throw new BookingException("Sorry only " + (capacity - size) + " bookings available");

        size += numberRequired;

        Booking id = new Booking(name, this.getClass().getTypeName(), numberRequired);

        bookings.put(id, id);

        return id;
    }

    void changeBooking(BookingId id, int numberOfSeats) throws BookingException {
        if (!bookings.containsKey(id))
            throw new BookingException("No such reservation");

        Booking booking = bookings.get(id);
        int newNumber = numberOfSeats - booking.getSize();

        if (newNumber > 0 && size + newNumber > capacity)
            throw new BookingException("Sorry only " + (capacity - size - booking.getSize()) + " bookings available");

        size += newNumber;

        if (numberOfSeats == 0)
            bookings.remove(id);
        else
            booking.setSize(newNumber);
    }

    Booking getBooking(BookingId bookingId) throws BookingException {
        if (bookings.containsKey(bookingId))
            return bookings.get(bookingId);

        throw new BookingException("No such reservation");
    }

    public void getBookings(List<Booking> bookings) {
        bookings.addAll(this.bookings.values());
    }
}