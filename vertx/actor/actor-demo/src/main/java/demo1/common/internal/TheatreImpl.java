package demo1.common.internal;

import demo1.common.actor.Booking;
import demo1.common.actor.BookingException;
import demo1.common.actor.BookingId;
import demo1.common.actor.Theatre;
import org.jboss.stm.annotations.LockFree;
import org.jboss.stm.annotations.ReadLock;
import org.jboss.stm.annotations.WriteLock;

import java.util.List;

public class TheatreImpl extends TripPart implements Theatre {
    public TheatreImpl(String name, int capacity) {
        this(null, name, capacity);
    }

    public TheatreImpl(String uid, String name, int capacity) {
        super(uid, name, capacity);
    }

    @Override
    @WriteLock
    public BookingId bookShow(String showName, int numberOfTickets) throws BookingException {
        return super.book(numberOfTickets);
    }

    @Override
    @WriteLock
    public void changeBooking(BookingId id, int numberOfTickets) throws BookingException {
        super.changeBooking(id, numberOfTickets);
    }

    @Override
    public Booking getBooking(BookingId bookingId) throws BookingException {
        return super.getBooking(bookingId);
    }

    @ReadLock
    public int getBookingCount() {
        return super.getBookingCount();
    }

    @ReadLock
    public int getCapacity() {
        return super.getCapacity();
    }

    @ReadLock
    public void getBookings(List<Booking> bookings) {
        super.getBookings(bookings);
    }

    @Override
    @LockFree
    public String getName() {
        return super.getName();
    }
}