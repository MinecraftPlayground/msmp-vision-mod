package dev.loat.msmp_vision.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Describes the environment attributes (light tints, ambient colours, ...) and the biome at the camera position.
 * Used for the log line of each screenshot, so differences to the client can be traced to concrete values.
 * Everything is read by reflection, so a renamed attribute never breaks the build.
 */
public final class EnvironmentLog {

    private EnvironmentLog() {}

    /**
     * @param level The level.
     * @param position The camera position.
     * @return One line, ex. {@code biome=minecraft:deep_dark; BLOCK_LIGHT_TINT=#FFD88C; ...}.
     */
    public static String describe(ServerLevel level, Vec3 position) {
        StringJoiner joiner = new StringJoiner("; ");
        joiner.add("biome=" + biome(level, position));
        Object attributes = level.environmentAttributes();
        Method getter = null;
        for (Method method : attributes.getClass().getMethods()) {
            if (method.getName().equals("getValue") && method.getParameterCount() == 2
                && method.getParameterTypes()[1].isAssignableFrom(Vec3.class)) {
                getter = method;
                break;
            }
        }
        if (getter != null) {
            for (Field field : EnvironmentAttributes.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    joiner.add(field.getName() + "=" + format(getter.invoke(attributes, field.get(null), position)));
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // attributes that cannot be read at this position are left out
                }
            }
        }
        return joiner.toString();
    }

    private static String biome(ServerLevel level, Vec3 position) {
        try {
            Object holder = level.getBiome(net.minecraft.core.BlockPos.containing(position));
            return String.valueOf(holder.getClass().getMethod("getRegisteredName").invoke(holder));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "?";
        }
    }

    private static String format(Object value) {
        if (value instanceof Integer color) {
            return "#%08X".formatted(color);
        }
        if (value instanceof Float number) {
            return String.format(Locale.ROOT, "%.4f", number);
        }
        if (value instanceof Double number) {
            return String.format(Locale.ROOT, "%.4f", number);
        }
        return String.valueOf(value);
    }
}
