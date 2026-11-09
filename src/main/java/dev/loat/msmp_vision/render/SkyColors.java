package dev.loat.msmp_vision.render;

/**
 * Colours of the daytime sky as Minecraft shows them, calibrated against client screenshots.
 *
 * <p>The sky is a vertical blend from a horizon colour to a zenith colour. Minecraft's own sky is produced by
 * fog applied to a dome, which gives a curve that is flat up to about 7 degrees above the horizon, rises quickly
 * until about 15 degrees and then slowly towards the zenith. The curve and the colour relations were measured on a
 * noon screenshot (fog {@code #C0D8FF}, sky {@code #6EB1FF}); other times of day scale from the same attributes.</p>
 *
 * <p>This class does not depend on Minecraft types.</p>
 */
public final class SkyColors {

    /** Fraction of the way from the fog colour to the sky colour at the horizon, per channel. */
    private static final float[] HORIZON_MIX = {0.159f, 0.205f, 0f};
    /** The zenith is the sky colour with the green channel reduced by this factor. */
    private static final float[] ZENITH_SCALE = {1f, 0.932f, 1f};

    /** Sky blend (0 = horizon colour, 1 = zenith colour) for elevations of 6, 7, 8, ... 30 degrees. */
    private static final float[] BLEND = {
        0.000f, 0.000f, 0.147f, 0.229f, 0.292f, 0.338f, 0.374f, 0.406f, 0.435f, 0.464f, 0.490f, 0.512f, 0.529f,
        0.543f, 0.558f, 0.572f, 0.587f, 0.599f, 0.609f, 0.618f, 0.628f, 0.638f, 0.645f, 0.652f, 0.659f
    };
    private static final int BLEND_FIRST_DEGREE = 6;
    private static final float BLEND_PLATEAU_DEGREES = 7f;
    private static final float BLEND_LAST_DEGREE = BLEND_FIRST_DEGREE + BLEND.length - 1;

    /** Additive brightness of the glow around the sun in colour units (0-255), by angle from the sun's centre. */
    private static final float[] GLOW_ANGLE = {0f, 2f, 4f, 5f, 7f, 9f, 11f, 13f, 15f, 17f, 19f};
    private static final float[] GLOW_VALUE = {54f, 47.5f, 41f, 38.3f, 31f, 22.8f, 15.3f, 8.5f, 3.2f, 0.7f, 0f};
    /** Angle beyond which the glow is zero, in degrees. */
    public static final float GLOW_RADIUS_DEGREES = 19f;

    private static final int SINE_STEPS = 1024;
    private static final float[] BLEND_BY_SINE = new float[SINE_STEPS + 1];

    static {
        for (int i = 0; i <= SINE_STEPS; i++) {
            BLEND_BY_SINE[i] = blend((float) Math.toDegrees(Math.asin(i / (double) SINE_STEPS)));
        }
    }

    private SkyColors() {}

    /**
     * The colour of the sky at the horizon, which is also the colour distant terrain fades to.
     *
     * @param fogRgb Fog colour attribute as {@code 0xRRGGBB}.
     * @param skyRgb Sky colour attribute as {@code 0xRRGGBB}.
     * @return The horizon colour as {@code 0xRRGGBB}.
     */
    public static int horizon(int fogRgb, int skyRgb) {
        int result = 0;
        for (int shift = 16, c = 0; shift >= 0; shift -= 8, c++) {
            float fog = (fogRgb >> shift) & 0xFF;
            float sky = (skyRgb >> shift) & 0xFF;
            result |= clamp(fog + (sky - fog) * HORIZON_MIX[c]) << shift;
        }
        return result;
    }

    /**
     * The colour of the sky overhead.
     *
     * @param skyRgb Sky colour attribute as {@code 0xRRGGBB}.
     * @return The zenith colour as {@code 0xRRGGBB}.
     */
    public static int zenith(int skyRgb) {
        int result = 0;
        for (int shift = 16, c = 0; shift >= 0; shift -= 8, c++) {
            result |= clamp(((skyRgb >> shift) & 0xFF) * ZENITH_SCALE[c]) << shift;
        }
        return result;
    }

    /**
     * Blend between horizon colour and zenith colour.
     *
     * @param elevationDegrees Angle above the horizon, negative below it.
     * @return 0 (horizon colour) to 1 (zenith colour).
     */
    public static float blend(float elevationDegrees) {
        if (elevationDegrees <= BLEND_PLATEAU_DEGREES) {
            return 0f;
        }
        if (elevationDegrees < BLEND_LAST_DEGREE) {
            float position = elevationDegrees - BLEND_FIRST_DEGREE;
            int index = (int) position;
            float fraction = position - index;
            return BLEND[index] + (BLEND[index + 1] - BLEND[index]) * fraction;
        }
        // Continuation towards the zenith: keeps the measured slope at the end of the table and reaches 1 at 90 degrees.
        float x = Math.min(1f, (elevationDegrees - BLEND_LAST_DEGREE) / (90f - BLEND_LAST_DEGREE));
        float last = BLEND[BLEND.length - 1];
        return last + (1f - last) * (1f - (float) Math.pow(1f - x, 1.3));
    }

    /**
     * Same as {@link #blend(float)}, but takes the sine of the elevation (the Y component of a normalized view
     * direction), which avoids a trigonometric call per pixel.
     *
     * @param sine Sine of the elevation, negative below the horizon.
     * @return 0 (horizon colour) to 1 (zenith colour).
     */
    public static float blendBySine(float sine) {
        if (sine <= 0f) {
            return 0f;
        }
        float position = Math.min(sine, 1f) * SINE_STEPS;
        int index = (int) position;
        if (index >= SINE_STEPS) {
            return BLEND_BY_SINE[SINE_STEPS];
        }
        float fraction = position - index;
        return BLEND_BY_SINE[index] + (BLEND_BY_SINE[index + 1] - BLEND_BY_SINE[index]) * fraction;
    }

    /**
     * Brightness of the glow around the sun at noon.
     *
     * @param angleDegrees Angle between the view direction and the centre of the sun.
     * @return Additive brightness in colour units (0-255).
     */
    public static float glow(float angleDegrees) {
        if (angleDegrees >= GLOW_RADIUS_DEGREES) {
            return 0f;
        }
        for (int i = 1; i < GLOW_ANGLE.length; i++) {
            if (angleDegrees <= GLOW_ANGLE[i]) {
                float t = (angleDegrees - GLOW_ANGLE[i - 1]) / (GLOW_ANGLE[i] - GLOW_ANGLE[i - 1]);
                return GLOW_VALUE[i - 1] + (GLOW_VALUE[i] - GLOW_VALUE[i - 1]) * t;
            }
        }
        return 0f;
    }

    private static int clamp(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }
}
