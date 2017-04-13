package demo.common.actor;

public class Booking extends BookingId {
    private String name;
    private String type;
    private int size;

    public Booking(String name, String type, int size) {
        this.name = name;
        this.type = type;
        this.size = size;
    }

    public String getName() {
        return name;
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