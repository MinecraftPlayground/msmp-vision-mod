package dev.loat.msmp_vision.config.files.rendering;

import dev.loat.config_lib.annotation.Annotation;


public class RenderingConfig {

    @Annotation.Comment("Edge length of a raster tile in pixels. One tile is one parallel work unit.")
    @Annotation.Key("tile-size")
    public int tileSize = 64;

    @Annotation.Comment("Fog starts at this fraction of the render distance.")
    @Annotation.Key("fog-start-factor")
    public double fogStartFactor = 0.75;

    @Annotation.Comment("Skip back-facing triangles. Disable only for debugging.")
    @Annotation.Key("backface-culling")
    public boolean backfaceCulling = true;
}
