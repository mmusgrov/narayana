package reproducer.actor;

public class BookingException extends Exception {
    public BookingException(String reason) {
        super(reason);
    }
}
