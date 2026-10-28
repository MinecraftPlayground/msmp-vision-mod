package dev.loat.msmp_vision.logging;

/**
 * Logger that prefixes every message with the ID of the RPC connection it belongs to.
 */
public class RPCConnectionLogger {

    private RPCConnectionLogger() {}

    public static void debug(Integer connectionId, String message) {
        Logger.debug("RPC Connection #%s: %s".formatted(connectionId, message));
    }

    public static void info(Integer connectionId, String message) {
        Logger.info("RPC Connection #%s: %s".formatted(connectionId, message));
    }

    public static void warning(Integer connectionId, String message) {
        Logger.warning("RPC Connection #%s: %s".formatted(connectionId, message));
    }

    public static void error(Integer connectionId, String message) {
        Logger.error("RPC Connection #%s: %s".formatted(connectionId, message));
    }
}
