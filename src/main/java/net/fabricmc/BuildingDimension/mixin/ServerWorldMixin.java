package net.fabricmc.BuildingDimension.mixin;

import net.fabricmc.BuildingDimension.BuildingDimension;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {

    @Inject(
            method = "onDimensionChanged(Lnet/minecraft/entity/Entity;)V",
            at = @At("HEAD")
    )
    private void onDimensionChanged(Entity entity, CallbackInfo ci){
        RegistryKey<World> target_dim = entity.getEntityWorld().getRegistryKey();
        if (target_dim.getValue().getNamespace().equals("building_dimension")) {
            BuildingDimension.log("Changing dimension from : " + target_dim.getValue());
        }
    }

    @Inject(
            method = "onPlayerChangeDimension(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At("HEAD")
    )
    private void onPlayerChangeDimension(ServerPlayerEntity player, CallbackInfo ci){
        RegistryKey<World> target_dim = player.getEntityWorld().getRegistryKey();
        if (target_dim.getValue().getNamespace().equals("building_dimension")) {
            BuildingDimension.log("Changing dimension from : " + target_dim.getValue());
        }
    }
}
