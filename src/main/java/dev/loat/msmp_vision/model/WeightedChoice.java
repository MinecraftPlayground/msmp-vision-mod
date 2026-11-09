package dev.loat.msmp_vision.model;

/**
 * Picks an entry of a weighted list, like Minecraft does for model variants.
 */
public final class WeightedChoice {

    private WeightedChoice() {}

    /**
     * @param weights The weights of all entries, each at least 1.
     * @param roll A random number in {@code [0, sum(weights))}.
     * @return The index of the entry the roll falls into.
     */
    public static int index(int[] weights, int roll) {
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }
}
