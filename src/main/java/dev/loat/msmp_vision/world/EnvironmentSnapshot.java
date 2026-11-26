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
 * @param moonPhase Name of the moon phase texture, ex. {@code full_moon}.
 * @param blockLightTint Tint of block light as {@code 0xRRGGBB}.
 * @param skyLightColor Colour of sky light as {@code 0xRRGGBB}.
 * @param ambientLightColor Ambient light added everywhere as {@code 0xRRGGBB}.
 * @param environmentFogStart Distance in blocks where the environmental fog begins.
 * @param environmentFogEnd Distance in blocks of full environmental fog.
 * @param cloudColor Cloud colour as {@code 0xAARRGGBB}; the alpha is the opacity next to the camera.
 * @param cloudFogEnd Distance in blocks where the clouds have faded out completely.
 */
public record EnvironmentSnapshot(
    double sunAngleRadians,
    float skyLightFactor,
    int fogColor,
    int skyColor,
    boolean hasSkyLight,
    long gameTime,
    boolean clouds,
    String moonPhase,
    int blockLightTint,
    int skyLightColor,
    int ambientLightColor,
    float environmentFogStart,
    float environmentFogEnd,
    int cloudColor,
    float cloudFogEnd
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

    /**
     * Creates a snapshot with a full moon.
     *
     * @param sunAngleRadians Sun angle in radians (0 = zenith).
     * @param skyLightFactor Sky light multiplier, 0 (night) to 1 (day).
     * @param fogColor Fog colour as {@code 0xRRGGBB}.
     * @param skyColor Sky colour as {@code 0xRRGGBB}.
     * @param hasSkyLight Whether the dimension has sky light.
     * @param gameTime Game time in ticks.
     * @param clouds Whether the dimension has clouds.
     */
    public EnvironmentSnapshot(double sunAngleRadians, float skyLightFactor, int fogColor, int skyColor, boolean hasSkyLight, long gameTime, boolean clouds) {
        this(sunAngleRadians, skyLightFactor, fogColor, skyColor, hasSkyLight, gameTime, clouds, "full_moon");
    }

    /**
     * Creates a snapshot with the overworld light colours.
     *
     * @param sunAngleRadians Sun angle in radians (0 = zenith).
     * @param skyLightFactor Sky light multiplier, 0 (night) to 1 (day).
     * @param fogColor Fog colour as {@code 0xRRGGBB}.
     * @param skyColor Sky colour as {@code 0xRRGGBB}.
     * @param hasSkyLight Whether the dimension has sky light.
     * @param gameTime Game time in ticks.
     * @param clouds Whether the dimension has clouds.
     * @param moonPhase Name of the moon phase texture.
     */
    public EnvironmentSnapshot(double sunAngleRadians, float skyLightFactor, int fogColor, int skyColor, boolean hasSkyLight, long gameTime, boolean clouds, String moonPhase) {
        this(sunAngleRadians, skyLightFactor, fogColor, skyColor, hasSkyLight, gameTime, clouds, moonPhase, 0xFFD88C, 0xFFFFFF, 0x0A0A0A);
    }

    /**
     * Creates a snapshot with the overworld environmental fog.
     *
     * @param sunAngleRadians Sun angle in radians (0 = zenith).
     * @param skyLightFactor Sky light multiplier, 0 (night) to 1 (day).
     * @param fogColor Fog colour as {@code 0xRRGGBB}.
     * @param skyColor Sky colour as {@code 0xRRGGBB}.
     * @param hasSkyLight Whether the dimension has sky light.
     * @param gameTime Game time in ticks.
     * @param clouds Whether the dimension has clouds.
     * @param moonPhase Name of the moon phase texture.
     * @param blockLightTint Tint of block light.
     * @param skyLightColor Colour of sky light.
     * @param ambientLightColor Ambient light colour.
     */
    public EnvironmentSnapshot(double sunAngleRadians, float skyLightFactor, int fogColor, int skyColor, boolean hasSkyLight, long gameTime, boolean clouds,
                               String moonPhase, int blockLightTint, int skyLightColor, int ambientLightColor) {
        this(sunAngleRadians, skyLightFactor, fogColor, skyColor, hasSkyLight, gameTime, clouds, moonPhase,
            blockLightTint, skyLightColor, ambientLightColor, 0f, 1024f);
    }

    /**
     * Creates a snapshot with the overworld cloud colour.
     *
     * @param sunAngleRadians Sun angle in radians (0 = zenith).
     * @param skyLightFactor Sky light multiplier, 0 (night) to 1 (day).
     * @param fogColor Fog colour as {@code 0xRRGGBB}.
     * @param skyColor Sky colour as {@code 0xRRGGBB}.
     * @param hasSkyLight Whether the dimension has sky light.
     * @param gameTime Game time in ticks.
     * @param clouds Whether the dimension has clouds.
     * @param moonPhase Name of the moon phase texture.
     * @param blockLightTint Tint of block light.
     * @param skyLightColor Colour of sky light.
     * @param ambientLightColor Ambient light colour.
     * @param environmentFogStart Distance where the environmental fog begins.
     * @param environmentFogEnd Distance of full environmental fog.
     */
    public EnvironmentSnapshot(double sunAngleRadians, float skyLightFactor, int fogColor, int skyColor, boolean hasSkyLight, long gameTime, boolean clouds,
                               String moonPhase, int blockLightTint, int skyLightColor, int ambientLightColor,
                               float environmentFogStart, float environmentFogEnd) {
        this(sunAngleRadians, skyLightFactor, fogColor, skyColor, hasSkyLight, gameTime, clouds, moonPhase,
            blockLightTint, skyLightColor, ambientLightColor, environmentFogStart, environmentFogEnd, 0xCCFFFFFF, 2048f);
    }

}
