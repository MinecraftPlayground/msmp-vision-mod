package dev.loat.msmp_vision.msmp.endpoints.screenshot.queue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.jsonrpc.api.Schema;


/**
 * Response payload for the {@code vision:screenshot/queue} method.
 *
 * <p>Example JSON representation:</p>
 * <pre><code>
 * { "id": "5b1c3c6e-8c53-4f43-9d5c-4a8f2f6a7c10" }
 * </code></pre>
 *
 * @param id Identifier of the queued screenshot, repeated in the completion notification
 */
public record ScreenshotQueueResponse(String id) {

    /**
     * Codec for serializing and deserializing {@link ScreenshotQueueResponse} instances.
     */
    public static final Codec<ScreenshotQueueResponse> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("id").forGetter(ScreenshotQueueResponse::id)
    ).apply(i, ScreenshotQueueResponse::new));

    /**
     * MSMP schema for {@link ScreenshotQueueResponse}, used for protocol discovery.
     */
    public static final Schema<ScreenshotQueueResponse> SCHEMA = Schema.record(CODEC)
        .withField("id", Schema.STRING_SCHEMA);
}
