package net.buildingdimension.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Exposes the internals {@link net.buildingdimension.dimension.DimensionFactory} needs to
 * construct and register a {@code ServerLevel} at runtime, the same way vanilla does for the
 * dimensions declared at world creation.
 */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {

    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess buildingDimension$getStorageSource();

    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> buildingDimension$getLevels();
}
