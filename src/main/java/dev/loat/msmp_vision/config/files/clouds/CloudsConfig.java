package dev.loat.msmp_vision.config.files.clouds;

import dev.loat.config_lib.annotation.Annotation;


public class CloudsConfig {

    @Annotation.Comment("Whether to render clouds in the overworld.")
    public boolean enabled = true;

    @Annotation.Comment("""
        Maximum distance of clouds from the camera in blocks. Minecraft's Cloud Distance option is given in chunks,
        64 chunks = 1024 blocks. Clouds fade out towards this distance.
    """)
    public int distance = 1024;

    @Annotation.Comment("Height of the underside of the cloud layer in blocks.")
    public double height = 192.33;
}
