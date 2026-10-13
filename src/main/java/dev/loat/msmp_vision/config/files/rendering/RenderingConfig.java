package dev.loat.msmp_vision.config.files.rendering;

import dev.loat.config_lib.annotation.Annotation;


public class RenderingConfig {
    @Annotation.Comment("Edge length of a raster tile in pixels. One tile is one parallel work unit.")
    public int tileSize = 64;

    @Annotation.Comment("Fog starts at this fraction of the render distance.")
    public double fogStartFactor = 0.75;

    @Annotation.Comment("Skip back-facing opaque triangles. Disable only for debugging.")
    public boolean backfaceCulling = true;
}
