package net.buildingdimension.fabric;

import net.buildingdimension.BuildingDimensionCommon;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class BuildingDimensionFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        BuildingDimensionCommon.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            BuildingDimensionCommon.registerCommands(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(BuildingDimensionCommon::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(BuildingDimensionCommon::onServerStopped);
        ServerTickEvents.END_SERVER_TICK.register(BuildingDimensionCommon::onServerTick);
    }
}
