package dev.loat.msmp_vision.config.files;

import dev.loat.config_lib.annotation.Annotation;
import dev.loat.msmp_vision.config.files.defaults.DefaultsConfig;
import dev.loat.msmp_vision.config.files.limits.LimitsConfig;
import dev.loat.msmp_vision.config.files.render.RenderConfig;


@Annotation.Comment("""
    Main configuration file for MSMP Vision.
""")
public class MSMPVisionConfigFile {

    private MSMPVisionConfigFile() {}

    @Annotation.Comment("Maximum time in seconds a single screenshot request may take.")
    @Annotation.Key("request-timeout-seconds")
    public int requestTimeoutSeconds = 30;

    @Annotation.Comment("Values used when a request does not specify a setting.")
    public DefaultsConfig defaults = new DefaultsConfig();

    @Annotation.Comment("Upper limits that requests are validated against. Requests exceeding them are rejected.")
    public LimitsConfig limits = new LimitsConfig();

    @Annotation.Comment("Tuning options of the rasterizer.")
    public RenderConfig render = new RenderConfig();
}
