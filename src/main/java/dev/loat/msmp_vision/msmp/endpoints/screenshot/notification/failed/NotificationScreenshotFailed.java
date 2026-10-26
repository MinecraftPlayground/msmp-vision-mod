package dev.loat.msmp_vision.msmp.endpoints.screenshot.notification.failed;

import dev.loat.msmp.MSMPNamespace;
import dev.loat.msmp.MSMPNotification;


/**
 * Registers the {@code vision:notification/screenshot/failed} MSMP notification.
 *
 * <p>Sent to the connection that queued a screenshot with {@code vision:screenshot/queue}
 * when the screenshot could not be completed. Not broadcast to other connections.</p>
 */
public class NotificationScreenshotFailed {

    private NotificationScreenshotFailed() {}

    /**
     * Registers the notification.
     *
     * @param namespace The namespace to register this notification under
     * @return The registered notification, used to send it
     */
    public static MSMPNotification<NotificationScreenshotFailedPayload> register(MSMPNamespace namespace) {
        return namespace.notification("screenshot/failed")
            .description("Sent to the requesting connection when a queued screenshot failed")
            .responseSchema(NotificationScreenshotFailedPayload.SCHEMA)
            .register();
    }
}
