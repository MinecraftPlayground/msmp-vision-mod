package dev.loat.msmp_vision.mesh;

import dev.loat.msmp_vision.assets.AssetManager;
import dev.loat.msmp_vision.assets.ResourceIds;
import dev.loat.msmp_vision.world.WorldSnapshot;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.world.level.DryFoliageColor;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Computes biome-dependent tint colours for grass, foliage and water.
 */
public final class TintResolver {

    private enum Kind { GRASS, FOLIAGE, SPRUCE, BIRCH }

    private static final Map<Block, Kind> KINDS = new Reference2ObjectOpenHashMap<>();

    static {
        for (Block block : new Block[]{Blocks.GRASS_BLOCK, Blocks.BUSH, Blocks.FERN, Blocks.LARGE_FERN, Blocks.SHORT_GRASS,
            Blocks.TALL_GRASS, Blocks.POTTED_FERN, Blocks.PINK_PETALS}) {
            KINDS.put(block, Kind.GRASS);
        }
        for (Block block : new Block[]{Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES,
            Blocks.MANGROVE_LEAVES, Blocks.VINE, Blocks.SUGAR_CANE}) {
            KINDS.put(block, Kind.FOLIAGE);
        }
        KINDS.put(Blocks.SPRUCE_LEAVES, Kind.SPRUCE);
        KINDS.put(Blocks.BIRCH_LEAVES, Kind.BIRCH);
    }

    /**
     * Loads the vanilla colour maps into the (static) vanilla colour lookup tables.
     *
     * @param assets The asset source.
     * @throws UncheckedIOException if a colour map cannot be read.
     */
    public static void initializeColorMaps(AssetManager assets) {
        GrassColor.init(read(assets, "minecraft:colormap/grass"));
        FoliageColor.init(read(assets, "minecraft:colormap/foliage"));
        DryFoliageColor.init(read(assets, "minecraft:colormap/dry_foliage"));
    }

    private static int[] read(AssetManager assets, String id) {
        byte[] bytes = assets.read(ResourceIds.texturePath(id));
        if (bytes == null) {
            throw new UncheckedIOException(new IOException("Missing colour map " + id));
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Resolves the tint of a block.
     *
     * @param world The world.
     * @param state The block state.
     * @param x World X.
     * @param y World Y.
     * @param z World Z.
     * @param blend Blend radius.
     * @return Tint as {@code 0xRRGGBB}; white if the block is not tinted.
     */
    public int blockTint(WorldSnapshot world, BlockState state, int x, int y, int z, int blend) {
        Kind kind = KINDS.get(state.getBlock());
        if (kind == null) {
            return 0xFFFFFF;
        }
        return switch (kind) {
            case SPRUCE -> FoliageColor.FOLIAGE_EVERGREEN & 0xFFFFFF;
            case BIRCH -> FoliageColor.FOLIAGE_BIRCH & 0xFFFFFF;
            case GRASS -> average(world, x, y, z, blend, (biome, bx, bz) -> biome.getGrassColor(bx, bz));
            case FOLIAGE -> average(world, x, y, z, blend, (biome, bx, bz) -> biome.getFoliageColor());
        };
    }

    /**
     * Resolves the water tint.
     *
     * @param world The world.
     * @param x World X.
     * @param y World Y.
     * @param z World Z.
     * @param blend Blend radius.
     * @return Tint as {@code 0xRRGGBB}.
     */
    public int waterTint(WorldSnapshot world, int x, int y, int z, int blend) {
        return average(world, x, y, z, blend, (biome, bx, bz) -> biome.getWaterColor());
    }

    @FunctionalInterface
    private interface ColorFunction {
        int apply(Biome biome, int x, int z);
    }

    private static int average(WorldSnapshot world, int x, int y, int z, int radius, ColorFunction function) {
        long r = 0;
        long g = 0;
        long b = 0;
        int samples = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Holder<Biome> holder = world.biome(x + dx, y, z + dz);
                if (holder == null) {
                    continue;
                }
                int color = function.apply(holder.value(), x + dx, z + dz);
                r += (color >> 16) & 0xFF;
                g += (color >> 8) & 0xFF;
                b += color & 0xFF;
                samples++;
            }
        }
        if (samples == 0) {
            return 0xFFFFFF;
        }
        return ((int) (r / samples) << 16) | ((int) (g / samples) << 8) | (int) (b / samples);
    }
}
