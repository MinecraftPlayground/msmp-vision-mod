package dev.loat.msmp_vision.config.files;

import dev.loat.config_lib.annotation.Annotation;


@Annotation.Comment("""
    This class represents the configuration file for the MSMPVision mod.
""")
public class MSMPVisionConfigFile {
    private MSMPVisionConfigFile() {}

    @Annotation.Comment("This is a sample configuration value.")
    public String myString = "default_value";

}
