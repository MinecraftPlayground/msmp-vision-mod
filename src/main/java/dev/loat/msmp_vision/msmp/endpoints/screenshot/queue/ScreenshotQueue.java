package dev.loat.msmp_vision.msmp.endpoints.screenshot.queue;

import dev.loat.msmp.MSMPNamespace;
import dev.loat.msmp_vision.VisionService;
import dev.loat.msmp_vision.logging.RPCConnectionLogger;
import dev.loat.msmp_vision.msmp.endpoints.screenshot.ScreenshotRequest;


/**
 * Registers the {@code vision:screenshot/queue} MSMP method.
 *
 * <p>Same parameters as {@code vision:screenshot}, but the call returns immediately with an {@code id}.
 * The world is captured in small slices over several server ticks, rendered on worker threads and the result is
 * delivered with the {@code vision:notification/screenshot/completed} (or {@code .../failed}) notification to the
 * connection that queued it. In contrast to {@code vision:screenshot}, the server keeps ticking meanwhile.</p>
 *
 * <p>Example request:</p>
 * <pre><code>
 * {
 *   "jsonrpc": "2.0",
 *   "id": 1,
 *   "method": "vision:screenshot/queue",
 *   "params": [{ "position": [128.5, 80.0, -32.3], "rotation": [90.0, 0.0] }]
 * }
 * </code></pre>
 *
 * <p>Example response:</p>
 * <pre><code>
 * { "id": "5b1c3c6e-8c53-4f43-9d5c-4a8f2f6a7c10" }
 * </code></pre>
 */
public class ScreenshotQueue {

    private ScreenshotQueue() {}

    /**
     * Registers the {@code vision:screenshot/queue} method on the given {@link MSMPNamespace}.
     *
     * @param namespace The namespace to register this method under
     * @param service The service handling the requests
     */
    public static void register(MSMPNamespace namespace, VisionService service) {

        namespace.method("screenshot/queue")
            .description("Queues a server-side screenshot and returns its id immediately; the image is delivered with a notification")
            .requestSchema(ScreenshotRequest.SCHEMA)
            .responseSchema(ScreenshotQueueResponse.SCHEMA)
            .register((server, client, params) -> {
                try {
                    String id = service.queue(server, client.connectionId(), params);
                    RPCConnectionLogger.info(client.connectionId(), "vision:screenshot/queue - queued " + id);
                    return new ScreenshotQueueResponse(id);
                } catch (RuntimeException e) {
                    RPCConnectionLogger.warning(client.connectionId(), "vision:screenshot/queue - " + e.getMessage());
                    throw e;
                }
            });
    }
}
