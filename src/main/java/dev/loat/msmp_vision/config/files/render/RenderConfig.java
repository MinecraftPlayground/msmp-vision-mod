package dev.loat.msmp_vision.config.files.render;

import dev.loat.config_lib.annotation.Annotation;


public class RenderConfig {

    @Annotation.Comment("""
        Number of render worker threads (meshing and rasterization).
        0 = automatic (available processors - 1, at least 1).
    """)
    public int threads = 0;

    @Annotation.Comment("Edge length of a raster tile in pixels. One tile is one parallel work unit.")
    @Annotation.Key("tile-size")
    public int tileSize = 64;

    @Annotation.Comment("Fog starts at this fraction of the render distance. Minecraft itself only fogs the last ~10%.")
    @Annotation.Key("fog-start-factor")
    public double fogStartFactor = 0.9;

    @Annotation.Comment("""
        Smooth lighting and ambient occlusion like Minecraft's \"Smooth Lighting\" option:
        light is blended across each block face and corners are darkened. Disable for flat lighting, which meshes a bit faster.
    """)
    @Annotation.Key("smooth-lighting")
    public boolean smoothLighting = true;

    @Annotation.Comment("""
        Empirical: how much brighter block light is than the plain light curve (0 = off, 1 = full bright).
        Measured on client screenshots of caves, see the environment attributes logged for each screenshot.
    """)
    @Annotation.Key("block-light-ambient")
    public double blockLightAmbient = 0.2;

    @Annotation.Comment("Skip back-facing triangles. Disable only for debugging.")
    @Annotation.Key("backface-culling")
    public boolean backfaceCulling = true;

    @Annotation.Comment("""
        Deflate level of the PNG encoder, 0 (no compression) to 9 (smallest file).
        1 encodes about 2.5x faster than 6 for a file that is roughly 30% larger.
    """)
    @Annotation.Key("png-compression-level")
    public int pngCompressionLevel = 1;

    @Annotation.Comment("""
        Renders a synthetic scene when the server starts so that the Java JIT compiler has already optimized the
        renderer. Without it the first screenshots after a start are several times slower.
    """)
    public boolean warmup = true;
}
