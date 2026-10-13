package dev.loat.msmp_vision.msmp.endpoints.screenshot;

import dev.loat.msmp.MSMPNamespace;


public class Screenshot {

    private Screenshot() {}

    /**
     * Registers the methods. Must be called during mod initialization, before the registry freezes.
     *
     * @param namespace The MSMP namespace to register under.
     * @param service The service handling the requests.
     */
    public static void register(MSMPNamespace namespace) {
        namespace.method("screenshot")
            .description("Renders a server-side screenshot and returns it as a Base64 encoded PNG.")
            .requestSchema(ScreenshotRequest.SCHEMA)
            .responseSchema(ScreenshotResponse.SCHEMA)
            .register((server, client, request) -> null);
    }
}
