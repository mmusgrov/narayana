package demo.trip2.internal;

import demo.common.actor.Booking;
import demo.common.actor.BookingException;
import demo.common.actor.BookingId;
import demo.common.actor.TaxiFirm;
import demo.common.actor.Theatre;
import demo.common.internal.RecoverableTripImplCommon;
import demo.trip2.actor.NestedTopLevelSTMTrip;
import demo.common.internal.TaxiFirmImpl;
import demo.common.internal.TheatreImpl;
import org.jboss.stm.internal.RecoverableContainer;

import java.util.Collection;

/**
 * Similar to {@link demo.trip1.internal.RecoverableTripImpl} but does not need to manage its own transactions because
 * {@link NestedTopLevelSTMTrip} is annotated with a {@link org.jboss.stm.annotations.NestedTopLevel}.
 */
public class RecoverableTripImpl extends RecoverableTripImplCommon implements NestedTopLevelSTMTrip {
    public RecoverableTripImpl(int capacity) {
        super(capacity);

        RecoverableContainer<Theatre> theatreContainer = new RecoverableContainer<>();
        RecoverableContainer<TaxiFirm> taxiContainer = new RecoverableContainer<>();

        setTheatre(theatreContainer.enlist(new TheatreImpl("Cats", getTheatreCapacity())));
        setPreferred(taxiContainer.enlist(new TaxiFirmImpl("favorite", getFavoriteTaxiCapacity())));
        setAltTaxi(taxiContainer.enlist(new TaxiFirmImpl("rival", getRivalTaxiCapacity())));
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
