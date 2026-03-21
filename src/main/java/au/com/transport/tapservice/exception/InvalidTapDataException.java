package au.com.transport.tapservice.exception;

/**
 * Custom exception to indicate invalid tap data during parsing.
 */
public class InvalidTapDataException extends RuntimeException {
    public InvalidTapDataException(String message) { super(message); }
}
