package dev.loat.msmp_vision.render;

import java.util.List;

import dev.loat.msmp_vision.config.files.MSMPVisionConfigFile;
import dev.loat.msmp_vision.config.files.defaults.DefaultsConfig;
import dev.loat.msmp_vision.config.files.limits.LimitsConfig;
import dev.loat.msmp_vision.msmp.endpoints.screenshot.ScreenshotRequest;
import dev.loat.msmp_vision.msmp.exceptions.InvalidParamsException;


/**
 * Immutable, validated parameters of a single render. The only place where request values, config defaults
 * and config limits are combined; every renderer stage reads from here.
 *
 * @param position Camera position as {@code [x, y, z]}.
 * @param yaw Yaw in degrees (Minecraft convention, 0 = south).
 * @param pitch Pitch in degrees (positive = down).
 * @param width Output width in pixels.
 * @param height Output height in pixels.
 * @param fov Vertical field of view in degrees.
 * @param renderDistance Render distance in chunks (1 chunk = 16 blocks), like in Minecraft's video settings.
 * @param ssaa Supersampling factor (1 = off).
 * @param biomeBlend Biome tint blend radius in blocks.
 */
public record RenderSettings(
    List<Double> position,
    double yaw,
    double pitch,
    int width,
    int height,
    double fov,
    int renderDistance,
    int ssaa,
    int biomeBlend
) {

    /** Pitch is clamped slightly below +-90 degrees so the camera's up vector never degenerates. */
    private static final double MAX_PITCH = 89.9;

    /**
     * Builds validated settings.
     *
     * @param request The request; omitted optional values select the config default.
     * @param config The configuration providing defaults and limits.
     * @return The settings.
     * @throws InvalidParamsException if a value is malformed or exceeds the configured limits.
     */
    public static RenderSettings from(ScreenshotRequest request, MSMPVisionConfigFile config) {
        DefaultsConfig defaultsConfig = config.defaults;
        LimitsConfig limitsConfig = config.limits;

        List<Double> position = request.position();
        require(position.size() == 3, "'position' must contain exactly 3 elements [x, y, z], got " + position.size());

        List<Double> rotation = request.rotation().orElse(List.of(0.0, 0.0));
        require(rotation.size() == 2, "'rotation' must contain exactly 2 elements [yaw, pitch], got " + rotation.size());

        require(position.stream().allMatch(Double::isFinite), "'position' must only contain finite numbers");
        require(rotation.stream().allMatch(Double::isFinite), "'rotation' must only contain finite numbers");

        int width = request.width().orElse(defaultsConfig.width);
        int height = request.height().orElse(defaultsConfig.height);
        double fov = request.fov().orElse(defaultsConfig.fov);
        int distance = request.renderDistance().orElse(defaultsConfig.renderDistanceChunks);
        int ssaa = request.ssaa().orElse(defaultsConfig.ssaa);
        int blend = request.biomeBlend().orElse(defaultsConfig.biomeBlend);

        require(width > 0, "'width' must be positive");
        require(height > 0, "'height' must be positive");
        require(distance > 0, "'render_distance' must be positive");
        require(ssaa > 0, "'ssaa' must be positive");
        require(blend >= 0, "'biome_blend' must not be negative");

        require(width <= limitsConfig.maxWidth, "'width' exceeds the limit of " + limitsConfig.maxWidth);
        require(height <= limitsConfig.maxHeight, "'height' exceeds the limit of " + limitsConfig.maxHeight);
        require(distance <= limitsConfig.maxRenderDistanceChunks, "'render_distance' exceeds the limit of " + limitsConfig.maxRenderDistanceChunks + " chunks");
        require(ssaa <= limitsConfig.maxSsaa, "'ssaa' exceeds the limit of " + limitsConfig.maxSsaa);
        require(fov >= limitsConfig.minFov && fov <= limitsConfig.maxFov, "'fov' must be between " + limitsConfig.minFov + " and " + limitsConfig.maxFov);
        require((long) width * ssaa * height * ssaa <= limitsConfig.maxRenderedPixels, "width*height*ssaa^2 exceeds " + limitsConfig.maxRenderedPixels + " pixels");

        return new RenderSettings(
            List.copyOf(position),
            rotation.get(0),
            Math.max(-MAX_PITCH, Math.min(MAX_PITCH, rotation.get(1))),
            width,
            height,
            fov,
            distance,
            ssaa,
            Math.min(blend, 4)
        );
    }

    /** @return The render distance in blocks. */
    public int renderDistanceBlocks() {
        return this.renderDistance * 16;
    }

    /** @return Framebuffer width including supersampling. */
    public int renderWidth() {
        return this.width * this.ssaa;
    }

    /** @return Framebuffer height including supersampling. */
    public int renderHeight() {
        return this.height * this.ssaa;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidParamsException(message);
        }
    }
}
