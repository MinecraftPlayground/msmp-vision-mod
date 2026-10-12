package dev.loat.template_server_mod.config.files;

import dev.loat.config_lib.annotation.Annotation;


@Annotation.Comment("""
    This class represents the configuration file for the TemplateServerMod mod.
""")
public class TemplateServerModConfigFile {
    private TemplateServerModConfigFile() {}

    @Annotation.Comment("This is a sample configuration value.")
    public String myString = "default_value";

}
