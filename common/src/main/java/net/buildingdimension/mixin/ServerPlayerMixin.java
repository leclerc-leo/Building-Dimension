package net.buildingdimension.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.buildingdimension.dimension.BuildingDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla has no notion of a building dimension's own bed/anchor, so a death there with none set
 * falls through {@code ServerPlayer#findRespawnPositionAndUseSpawnBlock} to
 * {@link TeleportTransition#createDefault}/{@code missingRespawnBlock}, both of which hardcode
 * {@code MinecraftServer#findRespawnDimension()} (the real overworld) — another escape route out of
 * a building dimension, the same class of bug {@link EntityMixin} already closes for portals: the
 * player would land back in the real world still in creative mode with their building-dimension
 * inventory. Redirect that fallback to respawn inside the building dimension itself, at its own
 * spawn point, whenever the player died there and vanilla would otherwise have sent them elsewhere.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @ModifyReturnValue(method = "findRespawnPositionAndUseSpawnBlock", at = @At("RETURN"))
    private TeleportTransition buildingDimension$keepRespawnInBuildingDimension(TeleportTransition original) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        ResourceKey<Level> deathDimension = player.level().dimension();

        if (!BuildingDimensions.isBuildingDimension(deathDimension) || BuildingDimensions.isBuildingDimension(original.newLevel().dimension())) {
            return original;
        }

        ServerLevel buildingLevel = player.level();
        LevelData.RespawnData respawnData = buildingLevel.getRespawnData();
        BlockPos spawnPos = player.adjustSpawnLocation(buildingLevel, respawnData.pos());

        return new TeleportTransition(
            buildingLevel,
            Vec3.atBottomCenterOf(spawnPos),
            Vec3.ZERO,
            respawnData.yaw(),
            respawnData.pitch(),
            TeleportTransition.DO_NOTHING
        );
    }
}
