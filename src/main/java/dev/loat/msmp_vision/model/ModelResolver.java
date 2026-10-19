package dev.loat.msmp_vision.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.loat.msmp_vision.assets.AssetManager;
import dev.loat.msmp_vision.assets.ResourceIds;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads block model JSON files and flattens their {@code parent} chain into a single {@link ResolvedModel}.
 */
public final class ModelResolver {

    private static final int MAX_DEPTH = 16;

    /**
     * A model with inherited textures and elements merged in.
     *
     * @param textures Texture variables ({@code name -> reference}).
     * @param elements The element array of the nearest ancestor defining one, or {@code null}.
     */
    public record ResolvedModel(Map<String, String> textures, JsonArray elements) {

        /**
         * Resolves a texture reference such as {@code #side} to a concrete texture identifier.
         *
         * @param reference A texture variable ({@code #name}) or a direct identifier.
         * @return The identifier, or {@code null} if the variable is undefined.
         */
        public String resolveTexture(String reference) {
            String current = reference;
            for (int i = 0; i < MAX_DEPTH && current != null && current.startsWith("#"); i++) {
                current = this.textures.get(current.substring(1));
            }
            return current == null || current.startsWith("#") ? null : current;
        }
    }

    private final AssetManager assets;
    private final ConcurrentHashMap<String, Optional<JsonObject>> rawCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ResolvedModel> resolvedCache = new ConcurrentHashMap<>();

    /**
     * Creates a resolver.
     *
     * @param assets The asset source.
     */
    public ModelResolver(AssetManager assets) {
        this.assets = assets;
    }

    /**
     * Resolves a model. Missing models yield an empty model (no elements).
     *
     * @param modelId Model identifier, e.g. {@code minecraft:block/stone}.
     * @return The resolved model, never {@code null}.
     */
    public ResolvedModel resolve(String modelId) {
        String key = ResourceIds.normalize(modelId);
        ResolvedModel cached = this.resolvedCache.get(key);
        if (cached != null) {
            return cached;
        }
        ResolvedModel resolved = this.build(key);
        this.resolvedCache.put(key, resolved);
        return resolved;
    }

    private ResolvedModel build(String modelId) {
        Map<String, String> textures = new HashMap<>();
        JsonArray elements = null;
        String current = modelId;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            JsonObject json = this.raw(current);
            if (json == null) {
                break;
            }
            if (json.has("textures")) {
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("textures").entrySet()) {
                    textures.putIfAbsent(entry.getKey(), textureName(entry.getValue()));
                }
            }
            if (elements == null && json.has("elements")) {
                elements = json.getAsJsonArray("elements");
            }
            current = json.has("parent") ? ResourceIds.normalize(json.get("parent").getAsString()) : null;
        }
        return new ResolvedModel(textures, elements);
    }

    private static String textureName(JsonElement value) {
        return value.isJsonObject() ? value.getAsJsonObject().get("sprite").getAsString() : value.getAsString();
    }

    private JsonObject raw(String modelId) {
        Optional<JsonObject> cached = this.rawCache.get(modelId);
        if (cached == null) {
            cached = Optional.ofNullable(this.assets.readJson(ResourceIds.modelPath(modelId)));
            this.rawCache.put(modelId, cached);
        }
        return cached.orElse(null);
    }
}
