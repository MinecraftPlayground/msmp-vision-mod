package dev.loat.msmp_vision.msmp.exceptions;

/**
 * Thrown when a method receives invalid or incomplete parameters.
 *
 * <p>Used for method-specific validation errors, such as a wrongly sized
 * {@code position} array or values exceeding the configured limits.</p>
 */
public class InvalidParamsException extends MSMPException {

    /**
     * @param message A description of which parameter is invalid and why
     */
    public InvalidParamsException(String message) {
        super(message);
    }
}
