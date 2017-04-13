package demo.common.internal;

import com.sun.javafx.binding.StringFormatter;
import demo.common.actor.Booking;
import demo.common.actor.BookingException;
import demo.common.actor.BookingId;
import demo.common.actor.TaxiFirm;
import demo.common.actor.Theatre;

import java.util.ArrayList;
import java.util.Collection;

public class RecoverableTripImplCommon {
    static int DEFAULT_CAPACITY = 20;

    public static String SHOW_NAME = "Cats";
    public static String FAVORITE_TAXI_NAME = "favorite";
    public static String RIVAL_TAXI_NAME = "rival";

    private int theatreCapacity;
    private int favoriteTaxiCapacity;
    private int rivalTaxiCapacity;

    private Theatre theatre;
    private TaxiFirm preferredTaxi;
    private TaxiFirm altTaxi;

    public RecoverableTripImplCommon() {
        this(DEFAULT_CAPACITY);
    }

    public RecoverableTripImplCommon(int capacity) {
        setCapacity(capacity);
    }

    public void setCapacity(int capacity) {
        this.theatreCapacity = capacity / 3;
        this.favoriteTaxiCapacity = capacity / 3;
        this.rivalTaxiCapacity = capacity / 3;
    }

    public Collection<BookingId> bookTrip(String showName, int numberOfSeats, int numberOfTaxiSpaces) throws BookingException {
        if (!theatre.getName().equals(showName))
            throw new BookingException(StringFormatter.format("%s is not showing%n", showName).get());

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

    protected void setTheatre(Theatre theatre) {
        this.theatre = theatre;
    }

    protected void setPreferred(TaxiFirm preferredTaxi) {
        this.preferredTaxi = preferredTaxi;
    }

    protected void setAltTaxi(TaxiFirm altTaxi) {
        this.altTaxi = altTaxi;
    }

    public int getTheatreCapacity() {
        return theatreCapacity;
    }

    public int getFavoriteTaxiCapacity() {
        return favoriteTaxiCapacity;
    }

    public int getRivalTaxiCapacity() {
        return rivalTaxiCapacity;
    }
}
