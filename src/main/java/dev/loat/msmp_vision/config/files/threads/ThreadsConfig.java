package dev.loat.msmp_vision.config.files.threads;

import dev.loat.config_lib.annotation.Annotation;


public class ThreadsConfig {
    @Annotation.Comment("""
        Number of render worker threads (meshing and rasterization).
        0 = automatic (available processors - 1, at least 1).
    """)
    public int render = 0;
}
