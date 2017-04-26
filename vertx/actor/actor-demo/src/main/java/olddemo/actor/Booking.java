package olddemo.actor;

import com.arjuna.ats.arjuna.common.Uid;

public class Booking extends BookingId {
    private String name;
    private String type;
    private String description;
    private int size;

    public Booking(Uid uid, String name, String description, String type, int size) {
        super(uid);

        this.name = name;
        this.description = description;
        this.type = type;
        this.size = size;
    }

    public Booking(String name, String description, String type, int size) {
        this(new Uid(), name, description, type, size);
    }

    public Booking(String uid, String name, String description, String type, int size) {
        this(new Uid(uid), name, description, type, size);
    }

    public String getId() { return super.getId(); }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getType() {
        return type;
    }

    public int getSize() {
        return size;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    };

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public void setSize(int size) {
        this.size = size;
    }
}