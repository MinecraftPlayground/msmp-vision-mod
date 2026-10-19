package dev.loat.msmp_vision.mesh;

/**
 * Meshing result of one section, split by render layer.
 *
 * @param opaque Quads rendered with depth write and no blending.
 * @param translucent Quads rendered back-to-front with alpha blending.
 */
public record SectionGeometry(QuadBuffer opaque, QuadBuffer translucent) {}
