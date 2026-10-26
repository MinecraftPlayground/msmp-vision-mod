package dev.loat.msmp_vision.msmp.endpoints.screenshot;

import dev.loat.msmp.MSMPNotification;
import dev.loat.msmp.MSMPServer;
import dev.loat.msmp_vision.msmp.endpoints.screenshot.notification.completed.NotificationScreenshotCompletedPayload;
import dev.loat.msmp_vision.msmp.endpoints.screenshot.notification.failed.NotificationScreenshotFailedPayload;

import java.util.function.Supplier;


/**
 * Sends the results of queued screenshots to the connection that requested them.
 *
 * <p>Must be used on the server thread.</p>
 */
public final class ScreenshotNotifier {

    private final MSMPNotification<NotificationScreenshotCompletedPayload> completed;
    private final MSMPNotification<NotificationScreenshotFailedPayload> failed;
    private final Supplier<MSMPServer> msmpServer;

    /**
     * @param completed The completion notification
     * @param failed The failure notification
     * @param msmpServer A supplier for the running {@link MSMPServer}, {@code null} when no server is running
     */
    public ScreenshotNotifier(
        MSMPNotification<NotificationScreenshotCompletedPayload> completed,
        MSMPNotification<NotificationScreenshotFailedPayload> failed,
        Supplier<MSMPServer> msmpServer
    ) {
        this.completed = completed;
        this.failed = failed;
        this.msmpServer = msmpServer;
    }

    /**
     * Sends a finished screenshot.
     *
     * @param connectionId The connection that queued the screenshot
     * @param id The screenshot id
     * @param response The rendered image
     */
    public void completed(Integer connectionId, String id, ScreenshotResponse response) {
        MSMPServer server = this.msmpServer.get();
        if (server == null) return;
        server.sendTo(connectionId, this.completed, new NotificationScreenshotCompletedPayload(
            id, response.image(), response.format(), response.width(), response.height(), response.renderTimeMs()
        ));
    }

    /**
     * Sends a failure.
     *
     * @param connectionId The connection that queued the screenshot
     * @param id The screenshot id
     * @param message What went wrong
     */
    public void failed(Integer connectionId, String id, String message) {
        MSMPServer server = this.msmpServer.get();
        if (server == null) return;
        server.sendTo(connectionId, this.failed, new NotificationScreenshotFailedPayload(id, message));
    }
}
