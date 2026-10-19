package dev.loat.msmp_vision.assets;

/**
 * Helpers for resource identifiers of the form {@code namespace:path}.
 */
public final class ResourceIds {

    private static final String DEFAULT_NAMESPACE = "minecraft";

    private ResourceIds() {}

    /**
     * Adds the default namespace if none is present.
     *
     * @param id An identifier with or without namespace.
     * @return The identifier including a namespace.
     */
    public static String normalize(String id) {
        return id.indexOf(':') >= 0 ? id : DEFAULT_NAMESPACE + ":" + id;
    }

    /**
     * Returns the namespace part of an identifier.
     *
     * @param id The identifier.
     * @return The namespace.
     */
    public static String namespace(String id) {
        String normalized = normalize(id);
        return normalized.substring(0, normalized.indexOf(':'));
    }

    /**
     * Returns the path part of an identifier.
     *
     * @param id The identifier.
     * @return The path.
     */
    public static String path(String id) {
        String normalized = normalize(id);
        return normalized.substring(normalized.indexOf(':') + 1);
    }

    /**
     * Returns the asset path of a model file.
     *
     * @param id The model identifier.
     * @return The relative asset path.
     */
    public static String modelPath(String id) {
        return "assets/" + namespace(id) + "/models/" + path(id) + ".json";
    }

    /**
     * Returns the asset path of a texture file.
     *
     * @param id The texture identifier.
     * @return The relative asset path.
     */
    public static String texturePath(String id) {
        return "assets/" + namespace(id) + "/textures/" + path(id) + ".png";
    }

    /**
     * Returns the asset path of a blockstate file.
     *
     * @param id The block identifier.
     * @return The relative asset path.
     */
    public static String blockStatePath(String id) {
        return "assets/" + namespace(id) + "/blockstates/" + path(id) + ".json";
    }
}
