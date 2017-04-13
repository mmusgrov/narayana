package demo.trip1.internal;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.coordinator.ActionStatus;
import demo.common.actor.Booking;
import demo.common.actor.BookingException;
import demo.common.actor.BookingId;
import demo.common.internal.RecoverableTripImplCommon;
import demo.trip1.actor.DefaultSTMTrip;
import demo.common.actor.TaxiFirm;
import demo.common.actor.Theatre;
import demo.common.internal.TaxiFirmImpl;
import demo.common.internal.TheatreImpl;
import org.jboss.stm.internal.RecoverableContainer;

import java.util.ArrayList;
import java.util.Collection;

public class RecoverableTripImpl extends RecoverableTripImplCommon implements DefaultSTMTrip {

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

    protected Collection<BookingId> bookTrip(Theatre theatre, int numberOfSeats, TaxiFirm preferredTaxi, TaxiFirm altTaxi, int numberOfTaxiSpaces) {
        Collection<BookingId> bookingIds = new ArrayList<>(2);
        AtomicAction A = new AtomicAction();
        BookingException reason = null;
        int res;

        A.begin();

        try {
            bookingIds.add(theatre.bookShow("Cats", numberOfSeats));

            try {
                bookingIds.add(preferredTaxi.bookTaxi(numberOfTaxiSpaces));
            } catch (BookingException e) {
                try {
                    bookingIds.add(altTaxi.bookTaxi(numberOfTaxiSpaces));
                } catch (BookingException e1) {
                    reason = e; // could not book any favorite or rival taxi so cancel common
                }
            }
        } catch (BookingException e) {
            reason = e; // could not book theatre so cancel common
        }

        if (reason != null) {
            System.out.printf("%s: Aborting common, reason: %s%n", Thread.currentThread().getName(), reason.getMessage());
            res = A.abort();
        } else {
            System.out.printf("%s: Committing common%n", Thread.currentThread().getName());
            res = A.commit();
        }

        System.out.printf("bookTrip using thread %s ended with status %s%n",
                Thread.currentThread().getName(), ActionStatus.stringForm(res));

        return bookingIds;
    }
}
