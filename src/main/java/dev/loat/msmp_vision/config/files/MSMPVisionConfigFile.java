package dev.loat.msmp_vision.config.files;

import dev.loat.config_lib.annotation.Annotation;
import dev.loat.msmp_vision.config.files.defaults.DefaultsConfig;
import dev.loat.msmp_vision.config.files.limits.LimitsConfig;
import dev.loat.msmp_vision.config.files.rendering.RenderingConfig;
import dev.loat.msmp_vision.config.files.threads.ThreadsConfig;


@Annotation.Comment("""
    Main configuration file for MSMP Vision.
""")
public class MSMPVisionConfigFile {

    private MSMPVisionConfigFile() {}

    @Annotation.Comment("Thread settings of the renderer.")
    public ThreadsConfig threads = new ThreadsConfig();

    @Annotation.Comment("Values used when a request does not specify a setting.")
    public DefaultsConfig defaults = new DefaultsConfig();

    @Annotation.Comment("Upper limits that requests are validated against. Requests exceeding them are rejected.")
    public LimitsConfig limits = new LimitsConfig();

    @Annotation.Comment("Tuning options of the rasterizer.")
    public RenderingConfig rendering = new RenderingConfig();

    @Annotation.Comment("Maximum time in seconds a single screenshot request may take.")
    @Annotation.Key("request-timeout-seconds")
    public int requestTimeoutSeconds = 30;
}
