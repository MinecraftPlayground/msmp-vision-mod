package dev.loat.msmp_vision.msmp.endpoints;

import dev.loat.msmp.MSMPNamespace;
import dev.loat.msmp.MSMPServer;
import dev.loat.msmp_vision.VisionService;
import dev.loat.msmp_vision.msmp.endpoints.screenshot.Screenshot;
import dev.loat.msmp_vision.msmp.endpoints.screenshot.ScreenshotNotifier;
import dev.loat.msmp_vision.msmp.endpoints.screenshot.notification.completed.NotificationScreenshotCompleted;
import dev.loat.msmp_vision.msmp.endpoints.screenshot.notification.failed.NotificationScreenshotFailed;
import dev.loat.msmp_vision.msmp.endpoints.screenshot.queue.ScreenshotQueue;

import java.util.function.Supplier;


/**
 * Central registration point for all {@code vision} MSMP endpoints.
 *
 * <p>Each endpoint is implemented in its own sub-package and registered here.</p>
 */
public class Endpoints {
    private Endpoints() {}

    /**
     * Registers all endpoints on the given {@link MSMPNamespace}.
     *
     * @param namespace The namespace to register all endpoints under
     * @param msmpServer A supplier for the MSMPServer instance, used by endpoints that push notifications
     * @param service The service that renders the screenshots
     */
    public static void register(MSMPNamespace namespace, Supplier<MSMPServer> msmpServer, VisionService service) {

        Screenshot.register(namespace, service);

        service.bindNotifier(new ScreenshotNotifier(
            NotificationScreenshotCompleted.register(namespace),
            NotificationScreenshotFailed.register(namespace),
            msmpServer
        ));
        ScreenshotQueue.register(namespace, service);
    }
}
