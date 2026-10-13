package dev.loat.msmp_vision.config.files.defaults;

import dev.loat.config_lib.annotation.Annotation;


public class DefaultsConfig {
    @Annotation.Comment("Output width in pixels.")
    public int width = 256;

    @Annotation.Comment("Output height in pixels.")
    public int height = 256;

    @Annotation.Comment("Vertical field of view in degrees.")
    public double fov = 70.0;

    @Annotation.Comment("Render distance in blocks.")
    public int renderDistance = 64;

    @Annotation.Comment("Supersampling factor (1 = off).")
    public int ssaa = 1;

    @Annotation.Comment("Biome tint blend radius in blocks (0 = off).")
    public int biomeBlend = 1;
}
