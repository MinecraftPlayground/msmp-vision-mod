package dev.loat.template_server_mod.config;

import dev.loat.config_lib.ConfigManager;
import dev.loat.template_server_mod.config.files.TemplateServerModConfigFile;


public class Config {
    
    private Config() {}
    
    private static final String ROOT_DIRECTORY = "template_server_mod";
    private static final ConfigManager CONFIG_MANAGER = new ConfigManager(ROOT_DIRECTORY);

    public static void register() {
        
        CONFIG_MANAGER.add("config.yml", TemplateServerModConfigFile.class);
    }

    public static TemplateServerModConfigFile getConfig() {

        return CONFIG_MANAGER.get(TemplateServerModConfigFile.class);
    }
}
