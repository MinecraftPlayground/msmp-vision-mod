package dev.loat.template_server_mod;

import dev.loat.template_server_mod.config.Config;
import dev.loat.template_server_mod.logging.Logger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * This class represents the main entry point for the TemplateServerMod mod.
 */
public class TemplateServerMod implements ModInitializer {

    /**
     * Called by Fabric during mod initialization, before the server starts.
     */
    @Override
    public void onInitialize() {
        Logger.setLoggerClass(this.getClass());

        Config.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            
        });

        Logger.info("Mod initialized.");
    }
}
