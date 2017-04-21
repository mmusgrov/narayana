package demo1.common.actor;

import com.arjuna.ats.arjuna.common.Uid;

public class BookingId {
    private Uid id;

    public BookingId() {
        id = new Uid();
    }

    public String getId() { return id.stringForm(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BookingId bookingId = (BookingId) o;

        return id != null ? id.equals(bookingId.id) : bookingId.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
