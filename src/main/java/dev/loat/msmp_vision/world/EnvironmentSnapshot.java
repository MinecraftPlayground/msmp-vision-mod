package dev.loat.msmp_vision.world;

/**
 * Time- and dimension-dependent values read from the level at capture time.
 *
 * @param sunAngleRadians Sun angle in radians (0 = zenith).
 * @param skyLightFactor Sky light multiplier, 0 (night) to 1 (day).
 * @param fogColor Fog colour as {@code 0xRRGGBB}; also the colour of the sky at the horizon.
 * @param skyColor Sky colour as {@code 0xRRGGBB}; the colour of the sky overhead.
 * @param hasSkyLight Whether the dimension has sky light.
 */
public record EnvironmentSnapshot(
    double sunAngleRadians,
    float skyLightFactor,
    int fogColor,
    int skyColor,
    boolean hasSkyLight
) {}
