package dev.loat.msmp_vision.msmp.endpoints.screenshot.notification.failed;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.jsonrpc.api.Schema;


/**
 * Payload for the {@code vision:notification/screenshot/failed} notification.
 *
 * <p>Example JSON representation:</p>
 * <pre><code>
 * { "id": "5b1c3c6e-8c53-4f43-9d5c-4a8f2f6a7c10", "message": "Rendering timed out after 30s" }
 * </code></pre>
 *
 * @param id Identifier returned by {@code vision:screenshot/queue}
 * @param message A human-readable description of what went wrong
 */
public record NotificationScreenshotFailedPayload(String id, String message) {

    /** Codec for serializing and deserializing {@link NotificationScreenshotFailedPayload} instances. */
    public static final Codec<NotificationScreenshotFailedPayload> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("id").forGetter(NotificationScreenshotFailedPayload::id),
        Codec.STRING.fieldOf("message").forGetter(NotificationScreenshotFailedPayload::message)
    ).apply(i, NotificationScreenshotFailedPayload::new));

    /** MSMP schema for {@link NotificationScreenshotFailedPayload}, used for protocol discovery. */
    public static final Schema<NotificationScreenshotFailedPayload> SCHEMA = Schema.record(CODEC)
        .withField("id", Schema.STRING_SCHEMA)
        .withField("message", Schema.STRING_SCHEMA);
}
