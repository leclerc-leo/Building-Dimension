package net.buildingdimension;

import com.mojang.brigadier.CommandDispatcher;
import net.buildingdimension.command.SwitchCommand;
import net.buildingdimension.command.SyncCommand;
import net.buildingdimension.config.BuildingDimensionConfig;
import net.buildingdimension.dimension.ChunkSync;
import net.buildingdimension.dimension.DimensionFactory;
import net.buildingdimension.platform.Services;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Loader-independent entry points. The Fabric and NeoForge subprojects delegate here from their
 * own lifecycle hooks, so all the mod logic lives in the common project.
 */
public class BuildingDimensionCommon {

    public static void init() {
        BuildingDimensionConfig.load();
        Constants.LOG.info("{} initialized on {}", Constants.MOD_NAME, Services.PLATFORM.getPlatformName());
    }

    /**
     * Called by each loader when the server's command dispatcher is being populated.
     */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        SwitchCommand.register(dispatcher);
        SyncCommand.register(dispatcher);
    }

    /**
     * Called by each loader once the server has finished starting, to recreate any building
     * dimensions that existed on a previous run.
     */
    public static void onServerStarted(MinecraftServer server) {
        DimensionFactory.restoreAll(server);
        applyConfiguredGameRules(server);
    }

    /**
     * Game rules are server-wide in vanilla (there's no per-dimension override), so this forces
     * them off for the whole server on startup when configured to, rather than just for building
     * dimensions.
     */
    private static void applyConfiguredGameRules(MinecraftServer server) {
        GameRules gameRules = server.getGameRules();
        if (BuildingDimensionConfig.disableMobGriefing()) {
            gameRules.set(GameRules.MOB_GRIEFING, false, server);
        }
        if (BuildingDimensionConfig.disableMobSpawning()) {
            gameRules.set(GameRules.SPAWN_MOBS, false, server);
        }
    }

    /**
     * Called by each loader once per server tick, to drain queued {@code /sync} chunk-copy work.
     */
    public static void onServerTick(MinecraftServer server) {
        ChunkSync.tick(server);
    }
}
