package dev.loat.msmp_vision.raster;

import dev.loat.msmp_vision.assets.TextureData;
import dev.loat.msmp_vision.render.RenderExecutor;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Multi-threaded tile based triangle rasterizer. Triangles are binned per tile, each tile is an independent job
 * (opaque pass first, then translucent triangles back-to-front), so no synchronization is needed on the framebuffer.
 */
public final class TileRasterizer {

    private static final int T = TriangleBuffer.TRIANGLE;
    private static final int V = TriangleBuffer.VERTEX;

    private final RenderExecutor executor;
    private final int tileSize;
    private final long timeoutSeconds;

    /**
     * Creates a rasterizer.
     *
     * @param executor Worker pool.
     * @param tileSize Tile edge length in pixels.
     * @param timeoutSeconds Maximum time for the whole raster pass.
     */
    public TileRasterizer(RenderExecutor executor, int tileSize, long timeoutSeconds) {
        this.executor = executor;
        this.tileSize = Math.max(8, tileSize);
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Rasterizes both layers into the framebuffer.
     *
     * @param opaque Opaque triangles (any order).
     * @param translucent Translucent triangles (sorted internally).
     * @param target The framebuffer, may already contain a sky.
     * @param textures Textures indexed by texture id.
     * @param fog Distance fog.
     */
    public void rasterize(TriangleBuffer opaque, TriangleBuffer translucent, Framebuffer target, TextureData[] textures, Fog fog) {
        int tilesX = (target.width() + this.tileSize - 1) / this.tileSize;
        int tilesY = (target.height() + this.tileSize - 1) / this.tileSize;

        IntArrayList[] opaqueBins = this.bin(opaque, null, tilesX, tilesY);
        IntArrayList[] translucentBins = this.bin(translucent, this.sortBackToFront(translucent), tilesX, tilesY);

        List<CompletableFuture<Void>> jobs = new ArrayList<>(tilesX * tilesY);
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                int bin = ty * tilesX + tx;
                if (opaqueBins[bin] == null && translucentBins[bin] == null) {
                    continue;
                }
                int x0 = tx * this.tileSize;
                int y0 = ty * this.tileSize;
                int x1 = Math.min(target.width(), x0 + this.tileSize);
                int y1 = Math.min(target.height(), y0 + this.tileSize);
                jobs.add(CompletableFuture.runAsync(() -> {
                    this.drawBin(opaque, opaqueBins[bin], false, x0, y0, x1, y1, target, textures, fog);
                    this.drawBin(translucent, translucentBins[bin], true, x0, y0, x1, y1, target, textures, fog);
                }, this.executor.executor()));
            }
        }
        this.executor.awaitAll(jobs, this.timeoutSeconds);
    }

    private int[] sortBackToFront(TriangleBuffer buffer) {
        long[] keys = new long[buffer.count()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = ((long) Float.floatToIntBits(buffer.depth(i)) << 32) | i;
        }
        Arrays.sort(keys);
        int[] order = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            order[i] = (int) keys[keys.length - 1 - i];
        }
        return order;
    }

    private IntArrayList[] bin(TriangleBuffer buffer, int[] order, int tilesX, int tilesY) {
        IntArrayList[] bins = new IntArrayList[tilesX * tilesY];
        float[] d = buffer.data();
        for (int n = 0; n < buffer.count(); n++) {
            int i = order == null ? n : order[n];
            int o = i * T;
            float minX = Math.min(d[o], Math.min(d[o + V], d[o + 2 * V]));
            float maxX = Math.max(d[o], Math.max(d[o + V], d[o + 2 * V]));
            float minY = Math.min(d[o + 1], Math.min(d[o + V + 1], d[o + 2 * V + 1]));
            float maxY = Math.max(d[o + 1], Math.max(d[o + V + 1], d[o + 2 * V + 1]));
            int tx0 = Math.max(0, (int) Math.floor(minX) / this.tileSize);
            int tx1 = Math.min(tilesX - 1, (int) Math.floor(maxX) / this.tileSize);
            int ty0 = Math.max(0, (int) Math.floor(minY) / this.tileSize);
            int ty1 = Math.min(tilesY - 1, (int) Math.floor(maxY) / this.tileSize);
            if (maxX < 0 || maxY < 0) {
                continue;
            }
            for (int ty = ty0; ty <= ty1; ty++) {
                for (int tx = tx0; tx <= tx1; tx++) {
                    int bin = ty * tilesX + tx;
                    if (bins[bin] == null) {
                        bins[bin] = new IntArrayList();
                    }
                    bins[bin].add(i);
                }
            }
        }
        return bins;
    }

    private void drawBin(TriangleBuffer buffer, IntArrayList bin, boolean translucentPass, int x0, int y0, int x1, int y1,
                         Framebuffer target, TextureData[] textures, Fog fog) {
        if (bin == null) {
            return;
        }
        for (int n = 0; n < bin.size(); n++) {
            int i = bin.getInt(n);
            TextureData texture = textures[buffer.texture(i)];
            if (texture != null) {
                this.drawTriangle(buffer.data(), i * T, texture, translucentPass, x0, y0, x1, y1, target, fog);
            }
        }
    }

    private void drawTriangle(float[] d, int o, TextureData texture, boolean translucentPass, int x0, int y0, int x1, int y1,
                              Framebuffer target, Fog fog) {
        float ax = d[o], ay = d[o + 1], aw = d[o + 2], au = d[o + 3], av = d[o + 4], ar = d[o + 5], ag = d[o + 6], ab = d[o + 7];
        float bx = d[o + V], by = d[o + V + 1], bw = d[o + V + 2], bu = d[o + V + 3], bv = d[o + V + 4], br = d[o + V + 5], bg = d[o + V + 6], bb = d[o + V + 7];
        float cx = d[o + 2 * V], cy = d[o + 2 * V + 1], cw = d[o + 2 * V + 2], cu = d[o + 2 * V + 3], cv = d[o + 2 * V + 4], cr = d[o + 2 * V + 5], cg = d[o + 2 * V + 6], cb = d[o + 2 * V + 7];

        double area = (double) (bx - ax) * (cy - ay) - (double) (by - ay) * (cx - ax);
        if (area == 0) {
            return;
        }
        double inv = 1.0 / area;
        int minX = Math.max(x0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
        int maxX = Math.min(x1 - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int minY = Math.max(y0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
        int maxY = Math.min(y1 - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
        if (minX > maxX || minY > maxY) {
            return;
        }

        double px = minX + 0.5;
        double py = minY + 0.5;
        double e0Row = (cx - bx) * (py - by) - (cy - by) * (px - bx);
        double e1Row = (ax - cx) * (py - cy) - (ay - cy) * (px - cx);
        double e2Row = (bx - ax) * (py - ay) - (by - ay) * (px - ax);
        double e0dx = -(cy - by), e0dy = cx - bx;
        double e1dx = -(ay - cy), e1dy = ax - cx;
        double e2dx = -(by - ay), e2dy = bx - ax;

        int[] color = target.color();
        float[] depth = target.depth();
        int width = target.width();

        for (int y = minY; y <= maxY; y++) {
            double e0 = e0Row, e1 = e1Row, e2 = e2Row;
            for (int x = minX; x <= maxX; x++) {
                double w0 = e0 * inv, w1 = e1 * inv, w2 = e2 * inv;
                e0 += e0dx;
                e1 += e1dx;
                e2 += e2dx;
                if (w0 < 0 || w1 < 0 || w2 < 0) {
                    continue;
                }
                int idx = y * width + x;
                float invW = (float) (w0 * aw + w1 * bw + w2 * cw);
                if (invW <= depth[idx]) {
                    continue;
                }
                float w = 1f / invW;
                float u = (float) (w0 * au + w1 * bu + w2 * cu) * w;
                float v = (float) (w0 * av + w1 * bv + w2 * cv) * w;
                int texel = texture.sample(u, v);
                int alpha = texel >>> 24;
                if (alpha == 0) {
                    continue;
                }
                float lr = (float) (w0 * ar + w1 * br + w2 * cr) * w;
                float lg = (float) (w0 * ag + w1 * bg + w2 * cg) * w;
                float lb = (float) (w0 * ab + w1 * bb + w2 * cb) * w;
                float f = fog.factor(x, y, w);
                int r = Math.min(255, (int) (((texel >> 16) & 0xFF) * lr * f + fog.r() * (1f - f)));
                int g = Math.min(255, (int) (((texel >> 8) & 0xFF) * lg * f + fog.g() * (1f - f)));
                int b = Math.min(255, (int) ((texel & 0xFF) * lb * f + fog.b() * (1f - f)));

                if (!translucentPass || alpha == 255) {
                    color[idx] = 0xFF000000 | (r << 16) | (g << 8) | b;
                    depth[idx] = invW;
                } else {
                    int dst = color[idx];
                    int inverse = 255 - alpha;
                    int or = (r * alpha + ((dst >> 16) & 0xFF) * inverse) / 255;
                    int og = (g * alpha + ((dst >> 8) & 0xFF) * inverse) / 255;
                    int ob = (b * alpha + (dst & 0xFF) * inverse) / 255;
                    color[idx] = 0xFF000000 | (or << 16) | (og << 8) | ob;
                }
            }
            e0Row += e0dy;
            e1Row += e1dy;
            e2Row += e2dy;
        }
    }
}
