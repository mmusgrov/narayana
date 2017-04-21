package reproducer.internal;

import reproducer.actor.Booking;
import reproducer.actor.BookingException;
import reproducer.actor.BookingId;
import reproducer.actor.TaxiFirm;
import reproducer.actor.Theatre;
import reproducer.actor.Trip;

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

    protected Collection<BookingId> bookTrip(Theatre theatre, int numberOfSeats, TaxiFirm preferredTaxi, TaxiFirm altTaxi, int numberOfTaxiSpaces) throws BookingException {
        Collection<BookingId> bookingIds = new ArrayList<>(2);

        bookingIds.add(theatre.bookShow("Cats", numberOfSeats));

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
