package demo1.common.internal;

import demo1.common.actor.Booking;
import demo1.common.actor.BookingException;
import demo1.common.actor.BookingId;
import demo1.common.actor.TaxiFirm;
import org.jboss.stm.annotations.LockFree;
import org.jboss.stm.annotations.ReadLock;
import org.jboss.stm.annotations.WriteLock;

import java.util.List;

public class TaxiFirmImpl extends TripPart implements TaxiFirm {
    public TaxiFirmImpl(String name, int capacity) {
        this(null, name, capacity);
    }

    public TaxiFirmImpl(String uid, String name, int capacity) {
        super(uid, name, capacity);
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
    @ReadLock
    public Booking getBooking(BookingId bookingId) throws BookingException {
        return super.getBooking(bookingId);
    }

    @ReadLock
    @Override
    public void getBookings(List<Booking> bookings) {
        super.getBookings(bookings);
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
