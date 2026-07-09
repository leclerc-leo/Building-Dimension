package net.buildingdimension.dimension;

import net.buildingdimension.Constants;
import net.buildingdimension.mixin.MinecraftServerAccessor;
import net.buildingdimension.mixin.ServerLevelAccessor;
import net.buildingdimension.persistence.DimensionRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;

import java.util.List;

/**
 * Creates the "building" counterpart of a dimension at runtime, the same way vanilla creates every
 * dimension declared at world creation ({@code MinecraftServer#createLevels}) — just later, and
 * with the source's own dimension type and chunk generator, so it works for modded dimensions too
 * without needing a datapack entry for each one.
 */
public final class DimensionFactory {

    private DimensionFactory() {
    }

    /**
     * Returns the building counterpart of {@code source}, creating it first if this is the first
     * time it has been requested.
     */
    public static ResourceKey<Level> getOrCreate(MinecraftServer server, ServerLevel source) {
        ResourceKey<Level> key = BuildingDimensions.buildingKeyOf(source.dimension());

        if (server.getLevel(key) == null) {
            create(server, source, key);
            DimensionRegistry.get(server).markCreated(source.dimension());
        }

        return key;
    }

    /**
     * Recreates every building dimension that existed when the server last stopped. Their chunk
     * data is untouched on disk; only the in-memory {@code ServerLevel} needs rebuilding.
     */
    public static void restoreAll(MinecraftServer server) {
        for (ResourceKey<Level> sourceKey : DimensionRegistry.get(server).createdSources()) {
            ServerLevel source = server.getLevel(sourceKey);
            if (source == null) {
                Constants.LOG.warn("Skipping building dimension restore for {}: source dimension is not loaded", sourceKey.identifier());
                continue;
            }

            ResourceKey<Level> key = BuildingDimensions.buildingKeyOf(sourceKey);
            if (server.getLevel(key) == null) {
                create(server, source, key);
            }
        }
    }

    private static void create(MinecraftServer server, ServerLevel source, ResourceKey<Level> key) {
        MinecraftServerAccessor serverAccessor = (MinecraftServerAccessor) server;
        boolean tickTime = ((ServerLevelAccessor) source).buildingDimension$getTickTime();

        LevelStem levelStem = new LevelStem(source.dimensionTypeRegistration(), source.getChunkSource().getGenerator());
        ServerLevelData levelData = new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData());
        long biomeZoomSeed = BiomeManager.obfuscateSeed(source.getSeed());

        ServerLevel level = new ServerLevel(
            server,
            Util.backgroundExecutor(),
            serverAccessor.buildingDimension$getStorageSource(),
            levelData,
            key,
            levelStem,
            server.getWorldData().isDebugWorld(),
            biomeZoomSeed,
            List.of(),
            tickTime
        );

        serverAccessor.buildingDimension$getLevels().put(key, level);
        Constants.LOG.info("Created building dimension {}", key.identifier());
    }
}
