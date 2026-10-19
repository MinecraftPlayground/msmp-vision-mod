package dev.loat.msmp_vision.render;

import java.util.List;

import dev.loat.msmp_vision.config.files.MSMPVisionConfigFile;
import dev.loat.msmp_vision.config.files.defaults.DefaultsConfig;
import dev.loat.msmp_vision.config.files.limits.LimitsConfig;
import dev.loat.msmp_vision.msmp.endpoints.screenshot.ScreenshotRequest;


/**
 * Immutable, validated parameters of a single render. The only place where request values, config defaults
 * and config limits are combined; every renderer stage reads from here.
 *
 * @param x Camera X.
 * @param y Camera Y.
 * @param z Camera Z.
 * @param yaw Yaw in degrees (Minecraft convention, 0 = south).
 * @param pitch Pitch in degrees (positive = down).
 * @param width Output width in pixels.
 * @param height Output height in pixels.
 * @param fov Vertical field of view in degrees.
 * @param renderDistance Render distance in blocks.
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

    /**
     * Builds validated settings.
     *
     * @param request The request; zero values select the config default.
     * @param config The configuration providing defaults and limits.
     * @return The settings.
     * @throws IllegalArgumentException if a value exceeds the configured limits.
     */
    public static RenderSettings from(ScreenshotRequest request, MSMPVisionConfigFile config) {
        DefaultsConfig defaultsConfig = config.defaults;
        LimitsConfig limitsConfig = config.limits;

        int width = request.width() > 0 ? request.width() : defaultsConfig.width;
        int height = request.height() > 0 ? request.height() : defaultsConfig.height;
        double fov = request.fov() > 0 ? request.fov() : defaultsConfig.fov;
        int distance = request.renderDistance() > 0 ? request.renderDistance() : defaultsConfig.renderDistance;
        int ssaa = request.ssaa() > 0 ? request.ssaa() : defaultsConfig.ssaa;
        int blend = request.biomeBlend() >= 0 ? request.biomeBlend() : defaultsConfig.biomeBlend;

        require(width <= limitsConfig.maxWidth, "width exceeds the limit of " + limitsConfig.maxWidth);
        require(height <= limitsConfig.maxHeight, "height exceeds the limit of " + limitsConfig.maxHeight);
        require(distance <= limitsConfig.maxRenderDistance, "renderDistance exceeds the limit of " + limitsConfig.maxRenderDistance);
        require(ssaa <= limitsConfig.maxSsaa, "ssaa exceeds the limit of " + limitsConfig.maxSsaa);
        require(fov >= limitsConfig.minFov && fov <= limitsConfig.maxFov, "fov must be between " + limitsConfig.minFov + " and " + limitsConfig.maxFov);
        require((long) width * ssaa * height * ssaa <= limitsConfig.maxRenderedPixels, "width*height*ssaa^2 exceeds " + limitsConfig.maxRenderedPixels + " pixels");

        return new RenderSettings(
            request.position(),
            request.yaw(),
            request.pitch(),
            width,
            height,
            fov,
            distance,
            ssaa,
            Math.min(blend, 4)
        );
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
            throw new IllegalArgumentException(message);
        }
    }
}
