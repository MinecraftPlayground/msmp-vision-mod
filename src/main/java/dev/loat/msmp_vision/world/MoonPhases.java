package dev.loat.msmp_vision.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * Finds the current moon phase. The API for it changed between versions, so it is looked up by reflection:
 * first the {@code MOON_PHASE} environment attribute, then {@code ServerLevel#getMoonPhase()}.
 * Falls back to a full moon.
 */
public final class MoonPhases {

    /** Phase names in the order of {@code getMoonPhase()} (0 = full moon). */
    private static final List<String> NAMES = List.of(
        "full_moon", "waning_gibbous", "third_quarter", "waning_crescent",
        "new_moon", "waxing_crescent", "first_quarter", "waxing_gibbous"
    );

    private MoonPhases() {}

    /**
     * @param level The level.
     * @param position The camera position.
     * @return The file name of the phase texture without extension, ex. {@code full_moon}.
     */
    public static String resolve(ServerLevel level, Vec3 position) {
        String result = null;
        try {
            Field field = EnvironmentAttributes.class.getField("MOON_PHASE");
            Object attributes = level.environmentAttributes();
            for (Method method : attributes.getClass().getMethods()) {
                if (method.getName().equals("getValue") && method.getParameterCount() == 2
                    && method.getParameterTypes()[1].isAssignableFrom(Vec3.class)) {
                    result = name(method.invoke(attributes, field.get(null), position));
                    break;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // fall through to the next source
        }
        if (result == null) {
            try {
                result = name(level.getClass().getMethod("getMoonPhase").invoke(level));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // fall through to the default
            }
        }
        return result != null ? result : NAMES.get(0);
    }

    private static String name(Object value) {
        if (value instanceof Number number) {
            return NAMES.get(Math.floorMod(number.intValue(), NAMES.size()));
        }
        if (value != null) {
            String text = (value instanceof Enum<?> e ? e.name() : String.valueOf(value)).toLowerCase(Locale.ROOT);
            for (String name : NAMES) {
                if (text.contains(name)) {
                    return name;
                }
            }
        }
        return null;
    }
}
