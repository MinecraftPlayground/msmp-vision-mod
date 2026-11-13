package dev.loat.msmp_vision.render;

import dev.loat.msmp_vision.mesh.QuadBuffer;

/**
 * Builds the quads of Minecraft's "fancy" clouds: a layer of 12x12 block cells with a thickness of 4 blocks,
 * where the alpha channel of {@code clouds.png} decides which cells exist. The pattern drifts with the game time.
 *
 * <p>Only faces that can be seen from the camera and that are not hidden by a neighbouring cell are created.
 * This class does not depend on Minecraft types.</p>
 */
public final class CloudGeometry {

    /** Edge length of one cloud cell in blocks. */
    public static final float CELL = 12f;
    /** Thickness of the cloud layer in blocks. */
    public static final float THICKNESS = 4f;
    /** Drift of the pattern in blocks per game tick. */
    public static final double SPEED = 0.03;
    /** Offset of the pattern along Z in cells. */
    public static final double Z_OFFSET_CELLS = 0.33;

    /** Brightness of each face, like in Minecraft: top, bottom, north/south, east/west. */
    private static final float TOP = 1.0f;
    private static final float BOTTOM = 0.7f;
    private static final float NORTH_SOUTH = 0.8f;
    private static final float EAST_WEST = 0.9f;

    /** Same vertex layout as the block faces, see {@code FaceGeometry}: DOWN, UP, NORTH, SOUTH, WEST, EAST. */
    private static final int[][][] VERTICES = {
        {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
        {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
        {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
        {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
        {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
        {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}
    };
    private static final float[] UV = {0, 0, 0, 1, 1, 1, 1, 0};

    private CloudGeometry() {}

    /**
     * Creates the quads of all cloud cells within {@code radius} of the camera.
     *
     * @param out Receives the quads, relative to the coordinate origin.
     * @param texture Texture id of the cloud texture.
     * @param mask Which cells exist, row-major ({@code maskWidth * maskHeight} entries).
     * @param maskWidth Width of the mask in cells.
     * @param maskHeight Height of the mask in cells.
     * @param cameraX Camera X in world coordinates.
     * @param cameraY Camera Y in world coordinates.
     * @param cameraZ Camera Z in world coordinates.
     * @param originX X of the coordinate origin all geometry is relative to.
     * @param originY Y of the coordinate origin.
     * @param originZ Z of the coordinate origin.
     * @param scrollX Drift of the pattern along X in blocks ({@code gameTime * SPEED}).
     * @param bottom Y of the underside of the layer.
     * @param radius Maximum distance of a cell from the camera in blocks.
     * @param brightness Brightness of the clouds, 0..1.
     * @return The number of quads created.
     */
    public static int build(
        QuadBuffer out, int texture, boolean[] mask, int maskWidth, int maskHeight,
        double cameraX, double cameraY, double cameraZ, int originX, int originY, int originZ,
        double scrollX, double bottom, double radius, float brightness
    ) {
        double shiftZ = Z_OFFSET_CELLS * CELL;
        double top = bottom + THICKNESS;
        int iMin = (int) Math.floor((cameraX + scrollX - radius) / CELL);
        int iMax = (int) Math.floor((cameraX + scrollX + radius) / CELL);
        int jMin = (int) Math.floor((cameraZ + shiftZ - radius) / CELL);
        int jMax = (int) Math.floor((cameraZ + shiftZ + radius) / CELL);

        boolean seeTop = cameraY > top;
        boolean seeBottom = cameraY < bottom;
        float[] pos = new float[12];
        int count = 0;

        for (int j = jMin; j <= jMax; j++) {
            for (int i = iMin; i <= iMax; i++) {
                if (!filled(mask, maskWidth, maskHeight, i, j)) {
                    continue;
                }
                double x0 = i * (double) CELL - scrollX;
                double z0 = j * (double) CELL - shiftZ;
                double x1 = x0 + CELL;
                double z1 = z0 + CELL;
                double dx = Math.max(Math.max(x0 - cameraX, cameraX - x1), 0);
                double dz = Math.max(Math.max(z0 - cameraZ, cameraZ - z1), 0);
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                float ox = (float) (x0 - originX);
                float oy = (float) (bottom - originY);
                float oz = (float) (z0 - originZ);

                if (seeTop) {
                    count += emit(out, texture, pos, 1, ox, oy, oz, rgb(brightness * TOP));
                }
                if (seeBottom) {
                    count += emit(out, texture, pos, 0, ox, oy, oz, rgb(brightness * BOTTOM));
                }
                if (cameraZ < z0 && !filled(mask, maskWidth, maskHeight, i, j - 1)) {
                    count += emit(out, texture, pos, 2, ox, oy, oz, rgb(brightness * NORTH_SOUTH));
                }
                if (cameraZ > z1 && !filled(mask, maskWidth, maskHeight, i, j + 1)) {
                    count += emit(out, texture, pos, 3, ox, oy, oz, rgb(brightness * NORTH_SOUTH));
                }
                if (cameraX < x0 && !filled(mask, maskWidth, maskHeight, i - 1, j)) {
                    count += emit(out, texture, pos, 4, ox, oy, oz, rgb(brightness * EAST_WEST));
                }
                if (cameraX > x1 && !filled(mask, maskWidth, maskHeight, i + 1, j)) {
                    count += emit(out, texture, pos, 5, ox, oy, oz, rgb(brightness * EAST_WEST));
                }
            }
        }
        return count;
    }

    private static boolean filled(boolean[] mask, int width, int height, int i, int j) {
        return mask[Math.floorMod(j, height) * width + Math.floorMod(i, width)];
    }

    private static int emit(QuadBuffer out, int texture, float[] pos, int face, float ox, float oy, float oz, int rgb) {
        int[][] vertices = VERTICES[face];
        for (int v = 0; v < 4; v++) {
            pos[v * 3] = vertices[v][0] * CELL;
            pos[v * 3 + 1] = vertices[v][1] * THICKNESS;
            pos[v * 3 + 2] = vertices[v][2] * CELL;
        }
        out.add(pos, ox, oy, oz, UV, texture, rgb);
        return 1;
    }

    private static int rgb(float value) {
        int c = Math.max(0, Math.min(255, Math.round(value * 255f)));
        return (c << 16) | (c << 8) | c;
    }
}
