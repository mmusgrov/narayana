package demo.common.internal;

import demo.common.actor.Booking;
import demo.common.actor.BookingException;
import demo.common.actor.BookingId;
import demo.common.actor.TaxiFirm;
import org.jboss.stm.annotations.LockFree;
import org.jboss.stm.annotations.ReadLock;
import org.jboss.stm.annotations.WriteLock;

public class TaxiFirmImpl extends TripPart implements TaxiFirm {
    public TaxiFirmImpl(String name, int capacity) {
        super(name, capacity);
    }

    @Override
    @WriteLock
    public BookingId bookTaxi(int numberOfSeats) throws BookingException {
        return super.book(numberOfSeats);
    }

    @Override
    @WriteLock
    public void changeBooking(BookingId id, int numberOfSeats) throws BookingException {
        super.changeBooking(id, numberOfSeats);
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
