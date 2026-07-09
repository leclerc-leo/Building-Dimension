package net.buildingdimension.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.buildingdimension.config.BuildingDimensionConfig;
import net.buildingdimension.dimension.BuildingDimensions;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla only exposes a single, server-wide "generate structures" toggle
 * ({@link WorldOptions#generateStructures()}), so carving out an exception for building
 * dimensions ({@link BuildingDimensionConfig#generateStructuresInBuildingDimensions()}) means
 * intercepting the check {@code ChunkStatusTasks} makes right before placing structure starts.
 */
@Mixin(ChunkStatusTasks.class)
public class ChunkStatusTasksMixin {

    @WrapOperation(
        method = "generateStructureStarts",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldOptions;generateStructures()Z")
    )
    private static boolean buildingDimension$suppressInBuildingDimensions(
        WorldOptions options,
        Operation<Boolean> original,
        WorldGenContext context,
        ChunkStep step,
        StaticCache2D<GenerationChunkHolder> chunks,
        ChunkAccess chunk
    ) {
        if (!original.call(options)) {
            return false;
        }

        boolean isBuildingDimension = BuildingDimensions.isBuildingDimension(context.level().dimension());
        return !isBuildingDimension || BuildingDimensionConfig.generateStructuresInBuildingDimensions();
    }
}
