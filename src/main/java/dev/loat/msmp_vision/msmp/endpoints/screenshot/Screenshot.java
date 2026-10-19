package dev.loat.msmp_vision.msmp.endpoints.screenshot;

import dev.loat.msmp.MSMPNamespace;
import dev.loat.msmp_vision.VisionService;


public class Screenshot {

    private Screenshot() {}

    /**
     * Registers the methods. Must be called during mod initialization, before the registry freezes.
     *
     * @param namespace The MSMP namespace to register under.
     * @param service The service handling the requests.
     */
    public static void register(MSMPNamespace namespace, VisionService service) {

        namespace.method("screenshot")
            .description("Renders a server-side screenshot and returns it as a Base64 encoded PNG.")
            .requestSchema(ScreenshotRequest.SCHEMA)
            .responseSchema(ScreenshotResponse.SCHEMA)
            .register((server, client, request) -> service.screenshot(server, request));
    }
}
