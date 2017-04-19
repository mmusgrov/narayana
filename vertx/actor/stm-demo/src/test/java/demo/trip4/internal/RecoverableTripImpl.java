package demo.trip4.internal;

import demo.common.actor.Booking;
import demo.common.actor.BookingException;
import demo.common.actor.BookingId;

import demo.common.internal.TaxiFirmImpl;
import demo.common.internal.TheatreImpl;
import demo.common.internal.RecoverableTripImplCommon;
import demo.trip4.actor.NoSTMTrip;

import java.util.Collection;

public class RecoverableTripImpl extends RecoverableTripImplCommon implements NoSTMTrip {

    public RecoverableTripImpl(int capacity) {
        super(capacity);

        // This version does not use the STM container to proxy service requests so must manage concurency ourselves
        setTheatre(new TheatreImpl("Cats", getTheatreCapacity()));
        setPreferred(new TaxiFirmImpl("favorite", getFavoriteTaxiCapacity()));
        setAltTaxi(new TaxiFirmImpl("rival", getRivalTaxiCapacity()));
    }

    @Override
    public Booking getBooking(BookingId id) throws BookingException {
        return super.getBooking(id);
    }

    @Override
    public Collection<BookingId> bookTrip(String showName, int numberOfSeats, int numberOfTaxiSpaces) throws BookingException {
        return super.bookTrip(showName, numberOfSeats, numberOfTaxiSpaces);
    }
}
