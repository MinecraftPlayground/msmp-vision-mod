package dev.loat.msmp_vision.msmp.exceptions;

/**
 * Thrown when a requested dimension identifier is not registered on the server.
 */
public class UnknownDimensionException extends MSMPException {

    /**
     * @param dimension The dimension identifier that could not be resolved
     */
    public UnknownDimensionException(String dimension) {
        super("Unknown dimension: " + dimension);
    }
}
