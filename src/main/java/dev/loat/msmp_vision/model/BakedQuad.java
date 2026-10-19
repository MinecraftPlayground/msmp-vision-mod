package dev.loat.msmp_vision.model;

import net.minecraft.core.Direction;

/**
 * A single textured quad of a baked block model in block-local space (0..1).
 *
 * @param positions 4 vertices x 3 floats, counter-clockwise when seen from outside.
 * @param uvs 4 vertices x 2 floats in texture space (0..1).
 * @param texture Texture id in the {@code TextureStore}.
 * @param face Direction the quad faces after rotation; used for shading and light sampling.
 * @param cullFace Direction in which an opaque neighbour hides this quad, or {@code null}.
 * @param tintIndex Tint index of the model face, {@code -1} for none.
 * @param shade Whether directional shading applies.
 * @param translucent Whether the texture requires alpha blending.
 */
public record BakedQuad(
    float[] positions,
    float[] uvs,
    int texture,
    Direction face,
    Direction cullFace,
    int tintIndex,
    boolean shade,
    boolean translucent
) {}
