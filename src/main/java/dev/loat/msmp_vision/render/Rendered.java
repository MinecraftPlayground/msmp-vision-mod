package dev.loat.msmp_vision.render;

import java.awt.image.BufferedImage;

/**
 * Result of a render together with the time each phase took.
 *
 * @param image The final image.
 * @param meshNanos Time spent meshing and projecting all sections.
 * @param skyNanos Time spent painting the sky.
 * @param tileNanos Time spent rasterizing the tiles.
 * @param downsampleNanos Time spent downsampling the supersampled framebuffer.
 */
public record Rendered(BufferedImage image, long meshNanos, long skyNanos, long tileNanos, long downsampleNanos) {}
