package reproducer.actor;

public class Booking extends BookingId {
    private String name;
    private String type;
    private String description;
    private int size;

    public Booking(String name, String description, String type, int size) {
        super();

        this.name = name;
        this.description = description;
        this.type = type;
        this.size = size;
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