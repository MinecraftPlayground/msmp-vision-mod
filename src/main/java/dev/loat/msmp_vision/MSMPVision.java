package dev.loat.msmp_vision;

import dev.loat.msmp_vision.config.Config;
import dev.loat.msmp_vision.logging.Logger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * This class represents the main entry point for the MSMPVision mod.
 */
public class MSMPVision implements ModInitializer {

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
