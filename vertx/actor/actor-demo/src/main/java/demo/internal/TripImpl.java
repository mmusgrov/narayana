package demo.internal;

import demo.actor.Booking;
import demo.actor.BookingException;
import demo.actor.BookingId;
import demo.actor.TaxiFirm;
import demo.actor.Theatre;
import demo.actor.Trip;

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
        return bookTrip(theatre, numberOfSeats, preferredTaxi, altTaxi, "taxiReference", numberOfTaxiSpaces);
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

    protected Collection<BookingId> bookTrip(Theatre theatre, int numberOfSeats,
                                             TaxiFirm preferredTaxi, TaxiFirm altTaxi,
                                             String taxiReference, int numberOfTaxiSpaces) throws BookingException {
        Collection<BookingId> bookingIds = new ArrayList<>(2);

        bookingIds.add(theatre.bookShow("Cats", numberOfSeats));

        try {
            bookingIds.add(preferredTaxi.bookTaxi(taxiReference, numberOfTaxiSpaces));
        } catch (BookingException e) {
            bookingIds.add(altTaxi.bookTaxi(taxiReference, numberOfTaxiSpaces));
        }

        return bookingIds;
    }

    public void getBookings(List<Booking> bookings) {
        theatre.getBookings(bookings);
        preferredTaxi.getBookings(bookings);
        altTaxi.getBookings(bookings);
    }
}
