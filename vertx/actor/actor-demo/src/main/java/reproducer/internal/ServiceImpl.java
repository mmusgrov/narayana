package reproducer.internal;

import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.arjuna.state.OutputObjectState;
import org.jboss.stm.annotations.RestoreState;
import org.jboss.stm.annotations.SaveState;
import reproducer.actor.Booking;
import reproducer.actor.BookingException;
import reproducer.actor.BookingId;
import org.jboss.stm.annotations.NotState;
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
    private int size;
    @State
    private Map<BookingId, Booking> bookings;

    @NotState
    private String uid; // TODO for debug purposes

    ServiceImpl(String uid, String name, int capacity) {
        this.uid = uid;
        this.name = name;
        this.capacity = capacity;
        this.bookings = new HashMap<>(capacity);
    }

    protected Map<BookingId, Booking> getBookings() {
        return bookings;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    String getName() {
        return name;
    }

    int getBookingCount() {
        return size;
    }

    int getCapacity() {
        return capacity;
    }

    BookingId book(String description, int numberRequired) throws BookingException {
        if (numberRequired <= 0)
            throw new BookingException("booking sizes should be greater than zero");

        if (size + numberRequired > capacity)
            throw new BookingException("Sorry only " + (capacity - size) + " bookings available");

        size += numberRequired;

        Booking id = new Booking(name, description, this.getClass().getTypeName(), numberRequired);

        getBookings().put(id, id);

        return id;
    }

    public BookingId book(int numberOfTickets) throws BookingException {
        return book("--", numberOfTickets);
    }

    void changeBooking(BookingId id, int numberOfSeats) throws BookingException {
        if (!getBookings().containsKey(id))
            throw new BookingException("No such reservation");

        Booking booking = getBookings().get(id);
        int newNumber = numberOfSeats - booking.getSize();

        if (newNumber > 0 && size + newNumber > capacity)
            throw new BookingException("Sorry only " + (capacity - size - booking.getSize()) + " bookings available");

        size += newNumber;

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

    public void getBookings(List<Booking> bookings) {
        bookings.addAll(getBookings().values());
    }


    @SaveState
    public void save_state (OutputObjectState os) throws IOException
    {
        os.packString(name);
        os.packInt(capacity);
        os.packInt(size);

        os.packInt(bookings.size());

        bookings.values().forEach(booking -> {
            try {
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
        size = os.unpackInt();

        int size = os.unpackInt();

        bookings.clear();

        for (int i = 0; i < size; i++) {
            Booking booking;

            booking = new Booking(
                    os.unpackString(),
                    os.unpackString(),
                    os.unpackString(),
                    os.unpackInt()
            );

            bookings.put(booking, booking);
        }

    }
}