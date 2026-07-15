package net.buildingdimension.neoforge;

import net.buildingdimension.BuildingDimensionCommon;
import net.buildingdimension.Constants;
import net.buildingdimension.neoforge.test.NeoForgeGameTests;
import net.buildingdimension.platform.Services;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(Constants.MOD_ID)
public class BuildingDimensionNeoForge {

    public BuildingDimensionNeoForge(IEventBus eventBus) {
        BuildingDimensionCommon.init();

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
            BuildingDimensionCommon.registerCommands(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
            BuildingDimensionCommon.onServerStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) ->
            BuildingDimensionCommon.onServerStopped(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
            BuildingDimensionCommon.onServerTick(event.getServer()));

        // Only wires the gametest registries in a dev environment (IDE run, gameTestServer task).
        // In a production/shipped jar, net.buildingdimension.test.* is stripped from the packaged
        // jar (see fabric/build.gradle and neoforge/build.gradle), so calling this unconditionally
        // would throw NoClassDefFoundError on every server/client boot.
        if (Services.PLATFORM.isDevelopmentEnvironment()) {
            NeoForgeGameTests.register(eventBus);
        }
    }
}
