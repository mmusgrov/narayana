package demo1.common.internal;

import demo1.common.actor.Booking;
import demo1.common.actor.BookingException;
import demo1.common.actor.BookingId;
import demo1.common.actor.TaxiFirm;
import demo1.common.actor.Theatre;
import demo1.common.actor.Trip;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TripImpl implements Trip {
    private Theatre theatre;
    private TaxiFirm preferredTaxi;
    private TaxiFirm altTaxi;

    public TripImpl(Theatre theatreProxy, TaxiFirm taxiProxy, TaxiFirm altTaxiProxy) {
        this.theatre = theatreProxy;
        this.preferredTaxi = taxiProxy;
        this.altTaxi = altTaxiProxy;
    }

    public Collection<BookingId> bookTrip(String showName, int numberOfSeats, int numberOfTaxiSpaces) throws BookingException {
//        if (!theatre.getName().equals(showName))
//            throw new BookingException(StringFormatter.format("%s is not showing%n", showName).get());

        return bookTrip(theatre, numberOfSeats, preferredTaxi, altTaxi, numberOfTaxiSpaces);
    }

    public Booking getBooking(BookingId id) throws BookingException {
        try {
            return theatre.getBooking(id);
        } catch (Exception e) {
            try {
                return preferredTaxi.getBooking(id);
            } catch (Exception e1) {
                try {
                    return altTaxi.getBooking(id);
                } catch (Exception e2) {
                    throw new BookingException("No such booking");
                }
            }
        }
    }

    @Override
    public void setCapacity(int capacity) {
        // ignore
    }

    protected Collection<BookingId> bookTrip(Theatre theatre, int numberOfSeats, TaxiFirm preferredTaxi, TaxiFirm altTaxi, int numberOfTaxiSpaces) throws BookingException {
        Collection<BookingId> bookingIds = new ArrayList<>(2);
int retries = 10;
Exception err = null;
while (retries-- != 0) {
    try {
//            AtomicAction a = new AtomicAction();
//            a.begin();
        bookingIds.add(theatre.bookShow("Cats", numberOfSeats));
        err = null;
//            a.commit();
    } catch (Exception e) {
        err = e;
    }
}

if (err != null)
    throw new BookingException("Theatre booking failed: " + err.getMessage());

        try {
                bookingIds.add(preferredTaxi.bookTaxi(numberOfTaxiSpaces));
            } catch (BookingException e) {
                bookingIds.add(altTaxi.bookTaxi(numberOfTaxiSpaces));
            }

        return bookingIds;
    }

    public void getBookings(List<Booking> bookings) {
        theatre.getBookings(bookings);
        preferredTaxi.getBookings(bookings);
        altTaxi.getBookings(bookings);
    }
}
