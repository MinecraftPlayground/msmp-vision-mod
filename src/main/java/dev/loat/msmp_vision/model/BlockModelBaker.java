package dev.loat.msmp_vision.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.loat.msmp_vision.assets.TextureData;
import dev.loat.msmp_vision.assets.TextureStore;
import dev.loat.msmp_vision.model.BlockStateSelector.ModelVariant;
import dev.loat.msmp_vision.model.ModelResolver.ResolvedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a {@link BlockState} into a list of {@link BakedQuad}s (block-local coordinates) and caches the result.
 *
 * <p>Supported: variants, multipart, parent inheritance, element rotation (incl. rescale), block x/y rotation,
 * UV rotation, tint indices, cull faces. Not yet supported: {@code uvlock}, item/entity-style builtin models.</p>
 */
public final class BlockModelBaker {

    private static final String MISSING_TEXTURE = "minecraft:missing";

    private final BlockStateSelector selector;
    private final ModelResolver models;
    private final TextureStore textures;
    private final ConcurrentHashMap<BlockState, List<BakedQuad>> cache = new ConcurrentHashMap<>();

    /**
     * Creates a baker.
     *
     * @param selector Blockstate evaluator.
     * @param models Model loader.
     * @param textures Texture registry.
     */
    public BlockModelBaker(BlockStateSelector selector, ModelResolver models, TextureStore textures) {
        this.selector = selector;
        this.models = models;
        this.textures = textures;
    }

    /**
     * Returns the quads of a block state.
     *
     * @param state The block state.
     * @return An immutable quad list, possibly empty.
     */
    public List<BakedQuad> bake(BlockState state) {
        return this.cache.computeIfAbsent(state, this::bakeUncached);
    }

    private List<BakedQuad> bakeUncached(BlockState state) {
        List<BakedQuad> quads = new ArrayList<>();
        for (ModelVariant variant : this.selector.select(state)) {
            ResolvedModel model = this.models.resolve(variant.model());
            JsonArray elements = model.elements();
            if (elements == null) {
                continue;
            }
            Quaternionf rotation = variant.x() == 0 && variant.y() == 0 ? null
                : new Quaternionf().rotationYXZ(-(float) Math.toRadians(variant.y()), -(float) Math.toRadians(variant.x()), 0f);
            for (JsonElement element : elements) {
                this.bakeElement(element.getAsJsonObject(), model, rotation, quads);
            }
        }
        return List.copyOf(quads);
    }

    private void bakeElement(JsonObject element, ResolvedModel model, Quaternionf rotation, List<BakedQuad> out) {
        float[] from = readVector(element.getAsJsonArray("from"));
        float[] to = readVector(element.getAsJsonArray("to"));
        Matrix4f transform = element.has("rotation") ? elementTransform(element.getAsJsonObject("rotation")) : null;
        boolean shade = !element.has("shade") || element.get("shade").getAsBoolean();

        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject("faces").entrySet()) {
            Direction direction = Direction.byName(entry.getKey());
            if (direction != null) {
                out.add(this.bakeFace(direction, entry.getValue().getAsJsonObject(), from, to, transform, rotation, model, shade));
            }
        }
    }

    private BakedQuad bakeFace(Direction direction, JsonObject face, float[] from, float[] to, Matrix4f transform,
                               Quaternionf rotation, ResolvedModel model, boolean shade) {
        float[] positions = new float[12];
        FaceGeometry.fill(positions, direction, from[0] / 16f, from[1] / 16f, from[2] / 16f, to[0] / 16f, to[1] / 16f, to[2] / 16f);

        Vector3f vertex = new Vector3f();
        for (int i = 0; i < 4; i++) {
            vertex.set(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]);
            if (transform != null) {
                transform.transformPosition(vertex);
            }
            if (rotation != null) {
                vertex.sub(0.5f, 0.5f, 0.5f);
                rotation.transform(vertex);
                vertex.add(0.5f, 0.5f, 0.5f);
            }
            positions[i * 3] = vertex.x;
            positions[i * 3 + 1] = vertex.y;
            positions[i * 3 + 2] = vertex.z;
        }

        Vector3f normal = new Vector3f(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        if (transform != null) {
            transform.transformDirection(normal).normalize();
        }
        if (rotation != null) {
            rotation.transform(normal);
        }
        Direction lightFace = Direction.getApproximateNearest(normal.x, normal.y, normal.z);

        Direction cullFace = null;
        if (face.has("cullface")) {
            Direction cull = Direction.byName(face.get("cullface").getAsString());
            if (cull != null) {
                Vector3f cullVector = new Vector3f(cull.getStepX(), cull.getStepY(), cull.getStepZ());
                if (rotation != null) {
                    rotation.transform(cullVector);
                }
                cullFace = Direction.getApproximateNearest(cullVector.x, cullVector.y, cullVector.z);
            }
        }

        String reference = face.has("texture") ? model.resolveTexture(face.get("texture").getAsString()) : null;
        TextureData texture = this.textures.get(reference != null ? reference : MISSING_TEXTURE);
        int tintIndex = face.has("tintindex") ? face.get("tintindex").getAsInt() : -1;

        return new BakedQuad(positions, bakeUvs(direction, face, from, to), texture.id(), lightFace, cullFace, tintIndex, shade, texture.translucent());
    }

    private static float[] bakeUvs(Direction direction, JsonObject face, float[] from, float[] to) {
        float[] uv = face.has("uv") ? readVector(face.getAsJsonArray("uv")) : defaultUv(direction, from, to);
        float[] corners = {uv[0], uv[1], uv[0], uv[3], uv[2], uv[3], uv[2], uv[1]};
        int rotation = face.has("rotation") ? face.get("rotation").getAsInt() : 0;
        int[] order = FaceGeometry.UV_ROTATION[Math.floorMod(rotation / 90, 4)];
        float[] uvs = new float[8];
        for (int i = 0; i < 4; i++) {
            uvs[i * 2] = corners[order[i] * 2] / 16f;
            uvs[i * 2 + 1] = corners[order[i] * 2 + 1] / 16f;
        }
        return uvs;
    }

    private static float[] defaultUv(Direction direction, float[] from, float[] to) {
        return switch (direction) {
            case DOWN -> new float[]{from[0], 16 - to[2], to[0], 16 - from[2]};
            case UP -> new float[]{from[0], from[2], to[0], to[2]};
            case NORTH -> new float[]{16 - to[0], 16 - to[1], 16 - from[0], 16 - from[1]};
            case SOUTH -> new float[]{from[0], 16 - to[1], to[0], 16 - from[1]};
            case WEST -> new float[]{from[2], 16 - to[1], to[2], 16 - from[1]};
            case EAST -> new float[]{16 - to[2], 16 - to[1], 16 - from[2], 16 - from[1]};
        };
    }

    private static Matrix4f elementTransform(JsonObject rotation) {
        float angle = (float) Math.toRadians(rotation.get("angle").getAsFloat());
        float[] origin = readVector(rotation.getAsJsonArray("origin"));
        String axis = rotation.get("axis").getAsString();
        float ox = origin[0] / 16f;
        float oy = origin[1] / 16f;
        float oz = origin[2] / 16f;

        Matrix4f matrix = new Matrix4f().translate(ox, oy, oz);
        switch (axis) {
            case "x" -> matrix.rotateX(angle);
            case "y" -> matrix.rotateY(angle);
            default -> matrix.rotateZ(angle);
        }
        if (rotation.has("rescale") && rotation.get("rescale").getAsBoolean()) {
            float scale = 1f / (float) Math.cos(angle);
            matrix.scale(axis.equals("x") ? 1f : scale, axis.equals("y") ? 1f : scale, axis.equals("z") ? 1f : scale);
        }
        return matrix.translate(-ox, -oy, -oz);
    }

    private static float[] readVector(JsonArray array) {
        float[] values = new float[array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = array.get(i).getAsFloat();
        }
        return values;
    }
}
