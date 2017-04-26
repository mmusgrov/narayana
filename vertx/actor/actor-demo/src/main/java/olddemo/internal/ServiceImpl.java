package olddemo.internal;

import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.arjuna.state.OutputObjectState;
import olddemo.actor.Booking;
import olddemo.actor.BookingException;
import olddemo.actor.BookingId;
import org.jboss.stm.annotations.RestoreState;
import org.jboss.stm.annotations.SaveState;
import org.jboss.stm.annotations.State;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceImpl {
    @State
    private String name;
    @State
    private int capacity;
    @State
    private int count; // the number of items booked
    @State
    private Map<BookingId, Booking> bookings; // individual bookings

    ServiceImpl(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
        count = 0;
        this.bookings = new HashMap<>(capacity);
    }

    protected Map<BookingId, Booking> getBookings() {
        return bookings;
    }

    String getName() {
        return name;
    }

    int getBookingCount() {
        return count;
    }

    int getCapacity() {
        return capacity;
    }

    BookingId book(String description, int numberRequired) throws BookingException {
        if (numberRequired <= 0)
            throw new BookingException("booking sizes should be greater than zero");

        if (count + numberRequired > capacity)
            throw new BookingException("Sorry only " + (capacity - count) + " bookings available");

        count += numberRequired;

        Booking id = new Booking(name, description, this.getClass().getTypeName(), numberRequired);

        getBookings().put(id, id);

        return id;
    }

    void changeBooking(BookingId id, int numberOfSeats) throws BookingException {
        if (!getBookings().containsKey(id))
            throw new BookingException("No such reservation");

        Booking booking = getBookings().get(id);
        int newNumber = numberOfSeats - booking.getSize();

        if (newNumber > 0 && count + newNumber > capacity)
            throw new BookingException("Sorry only " + (capacity - count - booking.getSize()) + " bookings available");

        count += newNumber;

        if (numberOfSeats == 0)
            getBookings().remove(id);
        else
            booking.setSize(newNumber);
    }

    Booking getBooking(BookingId bookingId) throws BookingException {
        if (getBookings().containsKey(bookingId))
            return getBookings().get(bookingId);

        throw new BookingException("No such reservation");
    }

    public void getBookings(List<Booking> bookingList) {
        bookingList.addAll(getBookings().values());
    }


    @SaveState
    public void save_state (OutputObjectState os) throws IOException
    {
        os.packString(name);
        os.packInt(capacity);
        os.packInt(count);

        os.packInt(bookings.size());

        getBookings().values().forEach(booking -> {
            try {
                os.packString(booking.getId());
                os.packString(booking.getName());
                os.packString(booking.getDescription());
                os.packString(booking.getType());
                os.packInt(booking.getSize());
            } catch (IOException e) {
                System.out.printf("THEATRE: save_state error %s%n", e.getMessage());
            }

        });
    }

    @RestoreState
    public void restore_state (InputObjectState os) throws IOException
    {
        name = os.unpackString();
        capacity = os.unpackInt();
        count = os.unpackInt();

        int noOfBookings = os.unpackInt();

        getBookings().clear();

        for (int i = 0; i < noOfBookings; i++) {
            Booking booking;

            booking = new Booking(
                    os.unpackString(),
                    os.unpackString(),
                    os.unpackString(),
                    os.unpackString(),
                    os.unpackInt()
            );

            getBookings().put(booking, booking);
        }
    }
}