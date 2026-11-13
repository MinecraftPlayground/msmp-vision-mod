package dev.loat.msmp_vision.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the chunks of recent screenshots loaded for a while, so that the next screenshot of the same area does not
 * have to load them from disk again. Without this, Minecraft unloads them a few seconds after a screenshot and the
 * next one is slow again.
 *
 * <p>The chunks are held with a loading-only ticket (they are not ticked, no mobs move there). Must be used on the
 * server thread.</p>
 */
public final class ChunkKeeper {

    private record Key(ResourceKey<Level> dimension, int x, int z) {}

    private record Entry(ServerLevel level, ChunkPos pos, long expiresNanos) {}

    private final Map<Key, Entry> entries = new HashMap<>();

    /**
     * Keeps a chunk loaded for the given time, counted from now.
     *
     * @param level The level.
     * @param x Chunk X.
     * @param z Chunk Z.
     * @param seconds How long to keep it, {@code 0} or less to not keep it.
     */
    public void keep(ServerLevel level, int x, int z, long seconds) {
        if (seconds <= 0) {
            return;
        }
        long expires = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        this.entries.put(new Key(level.dimension(), x, z), new Entry(level, new ChunkPos(x, z), expires));
    }

    /**
     * Refreshes the tickets of all kept chunks and drops the expired ones. Call once per server tick.
     */
    public void tick() {
        if (this.entries.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        Iterator<Entry> iterator = this.entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.expiresNanos() - now <= 0) {
                iterator.remove();
                continue;
            }
            entry.level().getChunkSource().addTicketWithRadius(TicketType.UNKNOWN, entry.pos(), 0);
        }
    }

    /** @return Number of chunks currently kept. */
    public int size() {
        return this.entries.size();
    }

    /** Forgets all chunks. */
    public void clear() {
        this.entries.clear();
    }
}
