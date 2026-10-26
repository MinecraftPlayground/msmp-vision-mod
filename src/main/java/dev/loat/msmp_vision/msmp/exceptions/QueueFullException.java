package dev.loat.msmp_vision.msmp.exceptions;

/**
 * Thrown when too many queued screenshots are pending.
 */
public class QueueFullException extends MSMPException {

    /**
     * @param limit The configured maximum number of pending screenshots
     */
    public QueueFullException(int limit) {
        super("Too many pending screenshots (limit %d), try again later".formatted(limit));
    }
}
