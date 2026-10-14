package dev.loat.msmp_vision.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.loat.msmp_vision.assets.AssetManager;
import dev.loat.msmp_vision.assets.ResourceIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates blockstate definition files ({@code variants} and {@code multipart}) for a concrete {@link BlockState}.
 */
public final class BlockStateSelector {

    /**
     * A model reference selected from a blockstate file.
     *
     * @param model Model identifier.
     * @param x X rotation in degrees.
     * @param y Y rotation in degrees.
     * @param uvlock Whether UVs are locked (currently not applied by the baker).
     */
    public record ModelVariant(String model, int x, int y, boolean uvlock) {}

    private final AssetManager assets;
    private final ConcurrentHashMap<String, Optional<JsonObject>> cache = new ConcurrentHashMap<>();

    /**
     * Creates a selector.
     *
     * @param assets The asset source.
     */
    public BlockStateSelector(AssetManager assets) {
        this.assets = assets;
    }

    /**
     * Selects the models to render for a block state.
     *
     * @param state The block state.
     * @return The selected model variants, empty if the block has no definition.
     */
    public List<ModelVariant> select(BlockState state) {
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        JsonObject definition = this.definition(blockId);
        if (definition == null) {
            return List.of();
        }
        if (definition.has("variants")) {
            for (Map.Entry<String, JsonElement> entry : definition.getAsJsonObject("variants").entrySet()) {
                if (matchesKey(state, entry.getKey())) {
                    return List.of(parseVariant(entry.getValue()));
                }
            }
            return List.of();
        }
        List<ModelVariant> result = new ArrayList<>();
        if (definition.has("multipart")) {
            for (JsonElement part : definition.getAsJsonArray("multipart")) {
                JsonObject object = part.getAsJsonObject();
                if (!object.has("when") || testCondition(state, object.getAsJsonObject("when"))) {
                    result.add(parseVariant(object.get("apply")));
                }
            }
        }
        return result;
    }

    private JsonObject definition(String blockId) {
        Optional<JsonObject> cached = this.cache.get(blockId);
        if (cached == null) {
            cached = Optional.ofNullable(this.assets.readJson(ResourceIds.blockStatePath(blockId)));
            this.cache.put(blockId, cached);
        }
        return cached.orElse(null);
    }

    private static ModelVariant parseVariant(JsonElement element) {
        JsonObject object = element.isJsonArray() ? element.getAsJsonArray().get(0).getAsJsonObject() : element.getAsJsonObject();
        return new ModelVariant(
            object.get("model").getAsString(),
            object.has("x") ? object.get("x").getAsInt() : 0,
            object.has("y") ? object.get("y").getAsInt() : 0,
            object.has("uvlock") && object.get("uvlock").getAsBoolean()
        );
    }

    private static boolean matchesKey(BlockState state, String key) {
        if (key.isEmpty() || key.equals("normal")) {
            return true;
        }
        for (String part : key.split(",")) {
            int separator = part.indexOf('=');
            if (separator < 0 || !propertyMatches(state, part.substring(0, separator), part.substring(separator + 1))) {
                return false;
            }
        }
        return true;
    }

    private static boolean testCondition(BlockState state, JsonObject when) {
        if (when.has("OR")) {
            for (JsonElement sub : when.getAsJsonArray("OR")) {
                if (testCondition(state, sub.getAsJsonObject())) {
                    return true;
                }
            }
            return false;
        }
        if (when.has("AND")) {
            JsonArray all = when.getAsJsonArray("AND");
            for (JsonElement sub : all) {
                if (!testCondition(state, sub.getAsJsonObject())) {
                    return false;
                }
            }
            return true;
        }
        for (Map.Entry<String, JsonElement> entry : when.entrySet()) {
            if (!propertyMatches(state, entry.getKey(), entry.getValue().getAsString())) {
                return false;
            }
        }
        return true;
    }

    private static boolean propertyMatches(BlockState state, String name, String expected) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                String actual = valueName(state, property);
                for (String candidate : expected.split("\\|")) {
                    if (candidate.equals(actual)) {
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
