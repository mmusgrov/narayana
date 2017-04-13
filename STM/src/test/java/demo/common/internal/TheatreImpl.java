package demo.common.internal;

import demo.common.actor.Booking;
import demo.common.actor.BookingException;
import demo.common.actor.BookingId;
import demo.common.actor.Theatre;
import org.jboss.stm.annotations.LockFree;
import org.jboss.stm.annotations.ReadLock;
import org.jboss.stm.annotations.WriteLock;

public class TheatreImpl extends TripPart implements Theatre {
    public TheatreImpl(String name, int capacity) {
        super(name, capacity);
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
    public Booking getBooking(BookingId bookingId) throws Exception {
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

    @Override
    @LockFree
    public String getName() {
        return super.getName();
    }
}