package dev.loat.msmp_vision.msmp.endpoints.screenshot.notification.completed;

import dev.loat.msmp.MSMPNamespace;
import dev.loat.msmp.MSMPNotification;


/**
 * Registers the {@code vision:notification/screenshot/completed} MSMP notification.
 *
 * <p>Sent to the connection that queued a screenshot with {@code vision:screenshot/queue}
 * as soon as the image is ready. Not broadcast to other connections.</p>
 */
public class NotificationScreenshotCompleted {

    private NotificationScreenshotCompleted() {}

    /**
     * Registers the notification.
     *
     * @param namespace The namespace to register this notification under
     * @return The registered notification, used to send it
     */
    public static MSMPNotification<NotificationScreenshotCompletedPayload> register(MSMPNamespace namespace) {
        return namespace.notification("screenshot/completed")
            .description("Sent to the requesting connection when a queued screenshot is ready")
            .responseSchema(NotificationScreenshotCompletedPayload.SCHEMA)
            .register();
    }
}
