package dev.loat.msmp_vision.config.files.limits;

import dev.loat.config_lib.annotation.Annotation;


public class LimitsConfig {

    @Annotation.Comment("Maximum output width.")
    @Annotation.Key("max-width")
    public int maxWidth = 1920;

    @Annotation.Comment("Maximum output height.")
    @Annotation.Key("max-height")
    public int maxHeight = 1080;

    @Annotation.Comment("Maximum render distance in chunks.")
    @Annotation.Key("max-render-distance")
    public int maxRenderDistance = 32;

    @Annotation.Comment("Maximum supersampling factor.")
    @Annotation.Key("max-ssaa")
    public int maxSsaa = 3;

    @Annotation.Comment("Maximum number of internally rendered pixels (width * height * ssaa^2).")
    @Annotation.Key("max-rendered-pixels")
    public int maxRenderedPixels = 8_000_000;

    @Annotation.Comment("Minimum field of view in degrees.")
    @Annotation.Key("min-fov")
    public double minFov = 30.0;

    @Annotation.Comment("Maximum field of view in degrees.")
    @Annotation.Key("max-fov")
    public double maxFov = 110.0;
}
