package au.com.transport.tapservice.exception;

public class FareNotFoundException extends RuntimeException {
    public FareNotFoundException(String from, String to) {
        super("No fare rule found for route: %s → %s".formatted(from, to));
    }
}
