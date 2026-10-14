package dev.loat.msmp_vision.model;

import net.minecraft.core.Direction;

/**
 * Vertex layout of the six cube faces, shared by the model baker and the fluid mesher.
 *
 * <p>Index order follows {@link Direction#ordinal()}: DOWN, UP, NORTH, SOUTH, WEST, EAST.</p>
 */
public final class FaceGeometry {

    /** {@code [face][vertex][x, y, z]} where 0 selects the minimum and 1 the maximum of the box. */
    private static final int[][][] VERTICES = {
        {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}}, // DOWN
        {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}}, // UP
        {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}}, // NORTH
        {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}}, // SOUTH
        {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}}, // WEST
        {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}  // EAST
    };

    /** UV corner permutation per face rotation (0, 90, 180, 270 degrees). */
    public static final int[][] UV_ROTATION = {{0, 1, 2, 3}, {1, 2, 3, 0}, {2, 3, 0, 1}, {3, 0, 1, 2}};

    private FaceGeometry() {}

    /**
     * Writes the four vertices of a face of an axis-aligned box.
     *
     * @param out Destination with at least 12 entries.
     * @param face The face.
     * @param minX Box minimum X.
     * @param minY Box minimum Y.
     * @param minZ Box minimum Z.
     * @param maxX Box maximum X.
     * @param maxY Box maximum Y.
     * @param maxZ Box maximum Z.
     */
    public static void fill(float[] out, Direction face, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        int[][] vertices = VERTICES[face.ordinal()];
        for (int v = 0; v < 4; v++) {
            out[v * 3] = vertices[v][0] == 0 ? minX : maxX;
            out[v * 3 + 1] = vertices[v][1] == 0 ? minY : maxY;
            out[v * 3 + 2] = vertices[v][2] == 0 ? minZ : maxZ;
        }
    }
}
