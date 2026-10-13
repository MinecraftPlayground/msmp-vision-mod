package dev.loat.msmp_vision.config.files.limits;

import dev.loat.config_lib.annotation.Annotation;


public class LimitsConfig {
    @Annotation.Comment("Maximum output width.")
    public int maxWidth = 1920;

    @Annotation.Comment("Maximum output height.")
    public int maxHeight = 1080;

    @Annotation.Comment("Maximum render distance in blocks.")
    public int maxRenderDistance = 128;

    @Annotation.Comment("Maximum supersampling factor.")
    public int maxSsaa = 3;

    @Annotation.Comment("Maximum number of internally rendered pixels (width * height * ssaa^2).")
    public int maxRenderedPixels = 8_000_000;

    @Annotation.Comment("Minimum field of view in degrees.")
    public double minFov = 30.0;

    @Annotation.Comment("Maximum field of view in degrees.")
    public double maxFov = 110.0;
}
