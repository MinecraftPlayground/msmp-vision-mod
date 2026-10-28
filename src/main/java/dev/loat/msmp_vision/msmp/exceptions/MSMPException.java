package dev.loat.msmp_vision.msmp.exceptions;

import net.minecraft.server.jsonrpc.methods.InvalidParameterJsonRpcException;


/**
 * Base exception for all MSMP vision errors.
 *
 * <p>All method-specific exceptions extend this class, allowing handlers
 * to catch all MSMP errors with a single {@code catch (MSMPException e)} block.</p>
 */
public class MSMPException extends InvalidParameterJsonRpcException {

    /**
     * Creates a new {@link MSMPException} with the given message.
     *
     * @param message A human-readable description of the error
     */
    public MSMPException(String message) {
        super(message);
    }
}
