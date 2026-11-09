package dev.loat.msmp_vision.model;

/**
 * Position based random seed, identical to Minecraft's {@code Mth.getSeed(x, y, z)}. Minecraft uses it to pick
 * a model variant per block (so that e.g. sand is not always rotated the same way).
 */
public final class BlockRandom {

    private BlockRandom() {}

    /**
     * @param x Block X.
     * @param y Block Y.
     * @param z Block Z.
     * @return The seed for that position.
     */
    public static long seed(int x, int y, int z) {
        long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }
}
