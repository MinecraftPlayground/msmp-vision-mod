package dev.loat.msmp_vision.msmp.endpoints.screenshot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.jsonrpc.api.Schema;


/**
 * Result of the {@code msmp_vision:screenshot} method.
 *
 * @param image Base64 encoded image data.
 * @param format Image format, currently always {@code png}.
 * @param width Image width in pixels.
 * @param height Image height in pixels.
 * @param renderTimeMs Total server-side time for the request in milliseconds.
 */
public record ScreenshotResponse(String image, String format, int width, int height, int renderTimeMs) {
 
    /**
     * Codec for serializing and deserializing {@link ScreenshotResponse} instances.
     */
    public static final Codec<ScreenshotResponse> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("image").forGetter(ScreenshotResponse::image),
        Codec.STRING.fieldOf("format").forGetter(ScreenshotResponse::format),
        Codec.INT.fieldOf("width").forGetter(ScreenshotResponse::width),
        Codec.INT.fieldOf("height").forGetter(ScreenshotResponse::height),
        Codec.INT.fieldOf("render_time_ms").forGetter(ScreenshotResponse::renderTimeMs)
    ).apply(instance, ScreenshotResponse::new));
 
    /**
     * MSMP schema for {@link ScreenshotResponse}, used for protocol discovery.
     */
    public static final Schema<ScreenshotResponse> SCHEMA = Schema.record(CODEC)
        .withField("image", Schema.STRING_SCHEMA)
        .withField("format", Schema.STRING_SCHEMA)
        .withField("width", Schema.INT_SCHEMA)
        .withField("height", Schema.INT_SCHEMA)
        .withField("render_time_ms", Schema.INT_SCHEMA);
}
