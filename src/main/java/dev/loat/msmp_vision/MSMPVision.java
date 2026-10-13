package dev.loat.msmp_vision;

import dev.loat.msmp.MSMPNamespace;
import dev.loat.msmp.MSMPServer;
import dev.loat.msmp_vision.config.Config;
import dev.loat.msmp_vision.logging.Logger;
import dev.loat.msmp_vision.msmp.endpoints.Endpoints;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.jsonrpc.ManagementServer;

/**
 * This class represents the main entry point for the MSMPVision mod.
 */
public class MSMPVision implements ModInitializer {

    /**
     * The shared {@code vision} namespace used for all MSMP registrations.
     * Attached to the running server in {@code SERVER_STARTED} and detached in {@code SERVER_STOPPED}.
     */
    private static final MSMPNamespace NS = new MSMPNamespace("vision");

    /**
     * Provides access to the {@link ManagementServer}
     * for broadcasting notifications. {@code null} when no server is running.
     */
    private static MSMPServer msmp;

    /**
     * Called by Fabric during mod initialization, before the server starts.
     * 
     * <p>Registers all MSMP methods and notifications and sets up server
     * lifecycle hooks for attaching and detaching the namespace.</p>
     */
    @Override
    public void onInitialize() {
        Logger.setLoggerClass(this.getClass());

        Config.register();

        Endpoints.register(NS, () -> msmp);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            NS.attach(server);
            msmp = new MSMPServer(server);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            NS.detach();
            msmp = null;
        });

        Logger.info("Mod initialized.");
    }
}
