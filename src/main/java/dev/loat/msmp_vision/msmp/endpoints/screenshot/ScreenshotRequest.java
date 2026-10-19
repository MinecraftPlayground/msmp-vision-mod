package dev.loat.msmp_vision.msmp.endpoints.screenshot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.jsonrpc.api.Schema;

import java.util.List;
import java.util.Optional;


/**
 * Request payload for the {@code vision:screenshot} method.
 *
 * <p>Only {@code position} is required. Every omitted optional field falls back to the
 * value from the {@code defaults} section of the config. Vectors are transferred as arrays,
 * exactly like in the {@code entity} namespace.</p>
 *
 * <p>Example JSON representations:</p>
 * <pre><code>
 * // Minimal
 * { "position": [128.5, 80.0, -32.3] }
 *
 * // Everything set
 * {
 *   "dimension": "minecraft:overworld",
 *   "position": [128.5, 80.0, -32.3],
 *   "rotation": [90.0, 0.0],
 *   "width": 512,
 *   "height": 288,
 *   "fov": 70.0,
 *   "render_distance": 64,
 *   "ssaa": 2,
 *   "biome_blend": 1
 * }
 * </code></pre>
 *
 * @param dimension The dimension resource key, if provided (default: {@code minecraft:overworld})
 * @param position The camera position as {@code [x, y, z]}
 * @param rotation The camera rotation as {@code [yaw, pitch]}, if provided (Minecraft convention: yaw 0 = south, pitch positive = down; default: {@code [0, 0]})
 * @param width The output width in pixels, if provided
 * @param height The output height in pixels, if provided
 * @param fov The vertical field of view in degrees, if provided
 * @param renderDistance The render distance in blocks, if provided
 * @param ssaa The supersampling factor (1 = off), if provided
 * @param biomeBlend The biome tint blend radius in blocks (0 = off), if provided
 */
public record ScreenshotRequest(
    Optional<String> dimension,
    List<Double> position,
    Optional<List<Double>> rotation,
    Optional<Integer> width,
    Optional<Integer> height,
    Optional<Double> fov,
    Optional<Integer> renderDistance,
    Optional<Integer> ssaa,
    Optional<Integer> biomeBlend
) {

    private static final Schema<List<Double>> POSITION_SCHEMA =
        Schema.ofType("array", Codec.DOUBLE.listOf());

    private static final Schema<List<Double>> ROTATION_SCHEMA =
        Schema.ofType("array", Codec.DOUBLE.listOf());

    /**
     * Codec for serializing and deserializing {@link ScreenshotRequest} instances.
     */
    public static final Codec<ScreenshotRequest> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.optionalFieldOf("dimension").forGetter(ScreenshotRequest::dimension),
        Codec.DOUBLE.listOf().fieldOf("position").forGetter(ScreenshotRequest::position),
        Codec.DOUBLE.listOf().optionalFieldOf("rotation").forGetter(ScreenshotRequest::rotation),
        Codec.INT.optionalFieldOf("width").forGetter(ScreenshotRequest::width),
        Codec.INT.optionalFieldOf("height").forGetter(ScreenshotRequest::height),
        Codec.DOUBLE.optionalFieldOf("fov").forGetter(ScreenshotRequest::fov),
        Codec.INT.optionalFieldOf("render_distance").forGetter(ScreenshotRequest::renderDistance),
        Codec.INT.optionalFieldOf("ssaa").forGetter(ScreenshotRequest::ssaa),
        Codec.INT.optionalFieldOf("biome_blend").forGetter(ScreenshotRequest::biomeBlend)
    ).apply(i, ScreenshotRequest::new));

    /**
     * MSMP schema for {@link ScreenshotRequest}, used for protocol discovery.
     */
    public static final Schema<ScreenshotRequest> SCHEMA = Schema.record(CODEC)
        .withField("dimension", Schema.STRING_SCHEMA)
        .withField("position", POSITION_SCHEMA)
        .withField("rotation", ROTATION_SCHEMA)
        .withField("width", Schema.INT_SCHEMA)
        .withField("height", Schema.INT_SCHEMA)
        .withField("fov", Schema.NUMBER_SCHEMA)
        .withField("render_distance", Schema.INT_SCHEMA)
        .withField("ssaa", Schema.INT_SCHEMA)
        .withField("biome_blend", Schema.INT_SCHEMA);
}
