package olddemo.internal;

import olddemo.actor.Booking;
import olddemo.actor.BookingException;
import olddemo.actor.BookingId;
import olddemo.actor.Theatre;
import org.jboss.stm.annotations.LockFree;
import org.jboss.stm.annotations.ReadLock;
import org.jboss.stm.annotations.WriteLock;

import java.util.List;

public class TheatreImpl extends ServiceImpl implements Theatre {

    public TheatreImpl(String name, int capacity) {
        super(name, capacity);
    }

    @Override
    @WriteLock
    public void initialize() {
    }

    @Override
    @WriteLock
    public BookingId bookShow(String showName, int numberOfTickets) throws BookingException {
        return super.book(showName, numberOfTickets);
    }

    @Override
    @WriteLock
    public void changeBooking(BookingId id, int numberOfTickets) throws BookingException {
        super.changeBooking(id, numberOfTickets);
    }

    @Override
    @ReadLock
    public Booking getBooking(BookingId bookingId) throws BookingException {
        return super.getBooking(bookingId);
    }

    @ReadLock
    public int getBookingCount() {
        return super.getBookingCount();
    }

    @LockFree
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