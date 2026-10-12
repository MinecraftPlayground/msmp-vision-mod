package dev.loat.msmp_vision.config;

import dev.loat.config_lib.ConfigManager;
import dev.loat.msmp_vision.config.files.MSMPVisionConfigFile;


public class Config {
    
    private Config() {}
    
    private static final String ROOT_DIRECTORY = "msmp/vision";
    private static final ConfigManager CONFIG_MANAGER = new ConfigManager(ROOT_DIRECTORY);

    public static void register() {
        
        CONFIG_MANAGER.add("config.yml", MSMPVisionConfigFile.class);
    }

    public static MSMPVisionConfigFile getConfig() {

        return CONFIG_MANAGER.get(MSMPVisionConfigFile.class);
    }
}
