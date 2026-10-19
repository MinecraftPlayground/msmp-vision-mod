package dev.loat.msmp_vision.msmp.endpoints.screenshot;

import dev.loat.msmp.MSMPNamespace;
import dev.loat.msmp_vision.VisionService;
import dev.loat.msmp_vision.logging.RPCConnectionLogger;


/**
 * Registers the {@code vision:screenshot} MSMP method.
 *
 * <p>Renders a server-side screenshot from the given position and rotation and returns it
 * as a Base64 encoded PNG. No client is involved, the image is rendered entirely on the server.</p>
 *
 * <p>Example request:</p>
 * <pre><code>
 * {
 *   "jsonrpc": "2.0",
 *   "id": 1,
 *   "method": "vision:screenshot",
 *   "params": [{
 *     "position": [128.5, 80.0, -32.3],
 *     "rotation": [90.0, 0.0],
 *     "width": 512,
 *     "height": 288
 *   }]
 * }
 * </code></pre>
 *
 * <p>Example response:</p>
 * <pre><code>
 * {
 *   "image": "iVBORw0KGgoAAAANSUhEUg...",
 *   "format": "png",
 *   "width": 512,
 *   "height": 288,
 *   "render_time_ms": 412
 * }
 * </code></pre>
 */
public class Screenshot {

    private Screenshot() {}

    /**
     * Registers the {@code vision:screenshot} method on the given {@link MSMPNamespace}.
     *
     * <p>Validation and rendering are delegated to {@link VisionService}. Values that are not
     * part of the request are taken from the {@code defaults} section of the config, values
     * exceeding the {@code limits} section are rejected.</p>
     *
     * @param namespace The namespace to register this method under
     * @param service The service handling the requests
     */
    public static void register(MSMPNamespace namespace, VisionService service) {

        namespace.method("screenshot")
            .description("Renders a server-side screenshot and returns it as a Base64 encoded PNG")
            .requestSchema(ScreenshotRequest.SCHEMA)
            .responseSchema(ScreenshotResponse.SCHEMA)
            .register((server, client, params) -> {
                try {
                    ScreenshotResponse response = service.screenshot(server, params);
                    RPCConnectionLogger.info(client.connectionId(),
                        "vision:screenshot - rendered %dx%d in %d ms".formatted(response.width(), response.height(), response.renderTimeMs()));
                    return response;
                } catch (RuntimeException e) {
                    RPCConnectionLogger.warning(client.connectionId(), "vision:screenshot - " + e.getMessage());
                    throw e;
                }
            });
    }
}
