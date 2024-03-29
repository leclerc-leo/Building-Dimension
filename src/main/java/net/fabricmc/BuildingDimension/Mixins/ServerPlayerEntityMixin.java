package net.fabricmc.BuildingDimension.Mixins;

import net.fabricmc.BuildingDimension.BuildingDimension;
import net.fabricmc.BuildingDimension.Commands.SwitchDimension;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {

    /**
     * Prevents players from leaving any building dimension.
     * Players must be able to leave the building dimension when using the /switch command.
     *
     * @param destination The destination
     * @param cir Callback info
     */
    @Inject(
            method = "moveToWorld",
            at = @At("HEAD"),
            cancellable = true
    )
    private void moveToWorld(ServerWorld destination, CallbackInfoReturnable<Entity> cir){
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;

        RegistryKey<World> target_dim = destination.getRegistryKey();
        RegistryKey<World> player_dim = player.getServerWorld().getRegistryKey();

        if (! target_dim.getValue().getNamespace().equals(BuildingDimension.MOD_ID) &&
                player_dim.getValue().getNamespace().equals(BuildingDimension.MOD_ID)) {

            if (! SwitchDimension.allowed_switching.contains(player.getUuid()))
                cir.setReturnValue(player);
        }
    }
}
