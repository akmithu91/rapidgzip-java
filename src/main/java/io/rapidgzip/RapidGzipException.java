package io.rapidgzip;

/**
 * Exception thrown when a rapidgzip native operation fails.
 */
public class RapidGzipException extends RuntimeException {

    public RapidGzipException(String message) {
        super(message);
    }

    public RapidGzipException(String message, Throwable cause) {
        super(message, cause);
    }
}
