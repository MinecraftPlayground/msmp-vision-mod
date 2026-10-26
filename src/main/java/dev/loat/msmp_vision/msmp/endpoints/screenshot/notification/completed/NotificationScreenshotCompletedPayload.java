package dev.loat.msmp_vision.msmp.endpoints.screenshot.notification.completed;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.jsonrpc.api.Schema;


/**
 * Payload for the {@code vision:notification/screenshot/completed} notification.
 *
 * <p>Example JSON representation:</p>
 * <pre><code>
 * {
 *   "id": "5b1c3c6e-8c53-4f43-9d5c-4a8f2f6a7c10",
 *   "image": "iVBORw0KGgoAAAANSUhEUg...",
 *   "format": "png",
 *   "width": 512,
 *   "height": 288,
 *   "render_time_ms": 1240
 * }
 * </code></pre>
 *
 * @param id Identifier returned by {@code vision:screenshot/queue}
 * @param image Base64 encoded image data (no data-URL prefix)
 * @param format Image format, currently always {@code png}
 * @param width Image width in pixels
 * @param height Image height in pixels
 * @param renderTimeMs Time from queuing until the image was ready, in milliseconds
 */
public record NotificationScreenshotCompletedPayload(String id, String image, String format, int width, int height, int renderTimeMs) {

    /** Codec for serializing and deserializing {@link NotificationScreenshotCompletedPayload} instances. */
    public static final Codec<NotificationScreenshotCompletedPayload> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("id").forGetter(NotificationScreenshotCompletedPayload::id),
        Codec.STRING.fieldOf("image").forGetter(NotificationScreenshotCompletedPayload::image),
        Codec.STRING.fieldOf("format").forGetter(NotificationScreenshotCompletedPayload::format),
        Codec.INT.fieldOf("width").forGetter(NotificationScreenshotCompletedPayload::width),
        Codec.INT.fieldOf("height").forGetter(NotificationScreenshotCompletedPayload::height),
        Codec.INT.fieldOf("render_time_ms").forGetter(NotificationScreenshotCompletedPayload::renderTimeMs)
    ).apply(i, NotificationScreenshotCompletedPayload::new));

    /** MSMP schema for {@link NotificationScreenshotCompletedPayload}, used for protocol discovery. */
    public static final Schema<NotificationScreenshotCompletedPayload> SCHEMA = Schema.record(CODEC)
        .withField("id", Schema.STRING_SCHEMA)
        .withField("image", Schema.STRING_SCHEMA)
        .withField("format", Schema.STRING_SCHEMA)
        .withField("width", Schema.INT_SCHEMA)
        .withField("height", Schema.INT_SCHEMA)
        .withField("render_time_ms", Schema.INT_SCHEMA);
}
