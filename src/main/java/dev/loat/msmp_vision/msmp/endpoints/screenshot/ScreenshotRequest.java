package dev.loat.msmp_vision.msmp.endpoints.screenshot;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.jsonrpc.api.Schema;

/**
 * Parameters of the {@code msmp_vision:screenshot} method.
 *
 * <p>Optional numeric fields use a sentinel value meaning "use the configured default":
 * {@code 0} for {@code width}, {@code height}, {@code fov}, {@code render_distance}, {@code ssaa}
 * and {@code -1} for {@code biome_blend}.</p>
 *
 * @param dimension Dimension id, e.g. {@code minecraft:overworld}.
 * @param position The target position as {@code [x, y, z]}
 * @param yaw Yaw in degrees (Minecraft convention, 0 = south, 90 = west).
 * @param pitch Pitch in degrees (positive = looking down).
 * @param width Output width in pixels, {@code 0} for default.
 * @param height Output height in pixels, {@code 0} for default.
 * @param fov Vertical field of view in degrees, {@code 0} for default.
 * @param renderDistance Render distance in blocks, {@code 0} for default.
 * @param ssaa Supersampling factor, {@code 0} for default.
 * @param biomeBlend Biome tint blend radius, {@code -1} for default.
 */
public record ScreenshotRequest(
    String dimension,
    List<Double> position,
    float yaw,
    float pitch,
    int width,
    int height,
    double fov,
    int renderDistance,
    int ssaa,
    int biomeBlend
) {
 
    /** Default dimension used when the request omits one. */
    public static final String DEFAULT_DIMENSION = "minecraft:overworld";

    private static final Schema<List<Double>> POSITION_SCHEMA =
        Schema.ofType("array", Codec.DOUBLE.listOf());
 
    /** Codec used for (de)serialization. */
    public static final Codec<ScreenshotRequest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.optionalFieldOf("dimension", DEFAULT_DIMENSION).forGetter(ScreenshotRequest::dimension),
        Codec.DOUBLE.listOf().fieldOf("position").forGetter(ScreenshotRequest::position),
        Codec.FLOAT.optionalFieldOf("yaw", 0f).forGetter(ScreenshotRequest::yaw),
        Codec.FLOAT.optionalFieldOf("pitch", 0f).forGetter(ScreenshotRequest::pitch),
        Codec.INT.optionalFieldOf("width", 0).forGetter(ScreenshotRequest::width),
        Codec.INT.optionalFieldOf("height", 0).forGetter(ScreenshotRequest::height),
        Codec.DOUBLE.optionalFieldOf("fov", 0.0).forGetter(ScreenshotRequest::fov),
        Codec.INT.optionalFieldOf("render_distance", 0).forGetter(ScreenshotRequest::renderDistance),
        Codec.INT.optionalFieldOf("ssaa", 0).forGetter(ScreenshotRequest::ssaa),
        Codec.INT.optionalFieldOf("biome_blend", -1).forGetter(ScreenshotRequest::biomeBlend)
    ).apply(instance, ScreenshotRequest::new));
 
    /** MSMP discovery schema. */
    public static final Schema<ScreenshotRequest> SCHEMA = Schema.record(CODEC)
        .withField("dimension", Schema.STRING_SCHEMA)
        .withField("position", POSITION_SCHEMA)
        .withField("yaw", Schema.NUMBER_SCHEMA)
        .withField("pitch", Schema.NUMBER_SCHEMA)
        .withField("width", Schema.INT_SCHEMA)
        .withField("height", Schema.INT_SCHEMA)
        .withField("fov", Schema.NUMBER_SCHEMA)
        .withField("render_distance", Schema.INT_SCHEMA)
        .withField("ssaa", Schema.INT_SCHEMA)
        .withField("biome_blend", Schema.INT_SCHEMA);
}
