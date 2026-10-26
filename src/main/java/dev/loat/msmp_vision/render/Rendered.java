package dev.loat.msmp_vision.render;

import java.awt.image.BufferedImage;

/**
 * Result of a render together with the time each phase took.
 *
 * @param image The final image.
 * @param meshNanos Time spent meshing and projecting all sections.
 * @param rasterNanos Time spent painting the sky, rasterizing and downsampling.
 */
public record Rendered(BufferedImage image, long meshNanos, long rasterNanos) {}
