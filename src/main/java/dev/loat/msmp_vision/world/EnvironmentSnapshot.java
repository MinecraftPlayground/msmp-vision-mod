package dev.loat.msmp_vision.world;

/**
 * Time- and dimension-dependent values read from the level at capture time.
 *
 * @param sunAngleRadians Sun angle in radians (0 = zenith).
 * @param skyLightFactor Sky light multiplier, 0 (night) to 1 (day).
 * @param fogColor Fog colour as {@code 0xRRGGBB}; also the colour of the sky at the horizon.
 * @param skyColor Sky colour as {@code 0xRRGGBB}; the colour of the sky overhead.
 * @param hasSkyLight Whether the dimension has sky light.
 * @param gameTime Game time in ticks; the clouds drift with it.
 * @param clouds Whether the dimension has clouds (the overworld).
 */
public record EnvironmentSnapshot(
    double sunAngleRadians,
    float skyLightFactor,
    int fogColor,
    int skyColor,
    boolean hasSkyLight,
    long gameTime,
    boolean clouds
) {

    /**
     * Creates a snapshot of a dimension without clouds.
     *
     * @param sunAngleRadians Sun angle in radians (0 = zenith).
     * @param skyLightFactor Sky light multiplier, 0 (night) to 1 (day).
     * @param fogColor Fog colour as {@code 0xRRGGBB}.
     * @param skyColor Sky colour as {@code 0xRRGGBB}.
     * @param hasSkyLight Whether the dimension has sky light.
     */
    public EnvironmentSnapshot(double sunAngleRadians, float skyLightFactor, int fogColor, int skyColor, boolean hasSkyLight) {
        this(sunAngleRadians, skyLightFactor, fogColor, skyColor, hasSkyLight, 0L, false);
    }
}
