package dev.loat.msmp_vision.msmp.endpoints;

import dev.loat.msmp.MSMPNamespace;
import dev.loat.msmp.MSMPServer;
import dev.loat.msmp_vision.VisionService;
import dev.loat.msmp_vision.msmp.endpoints.screenshot.Screenshot;

import java.util.function.Supplier;


public class Endpoints {
    private Endpoints() {}

    /**
     * Registers all endpoints on the given {@link MSMPNamespace}.
     *
     * @param namespace The namespace to register all endpoints under
     * @param msmpServer A supplier for the MSMPServer instance, used by some endpoints to subscribe to server events
     */
    public static void register(MSMPNamespace namespace, Supplier<MSMPServer> msmpServer, VisionService service) {
        
        Screenshot.register(namespace, service);
    }
}
