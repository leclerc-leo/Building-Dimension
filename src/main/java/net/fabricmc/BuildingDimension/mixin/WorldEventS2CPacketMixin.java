package net.fabricmc.BuildingDimension.mixin;

import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(WorldEventS2CPacket.class)
public class WorldEventS2CPacketMixin {

    @Mutable
    @Final
    @Shadow
    private int eventId;

    /**
     * Prevents the server from sending the sound a portal frame makes.
     *
     * @param eventId The event ID
     * @param pos The position of the block
     * @param data The block state
     * @param global Whether the event is global
     * @param ci Callback info
     */
    @Inject(method = "<init>(ILnet/minecraft/util/math/BlockPos;IZ)V", at = @At("RETURN"))
    private void init(int eventId, BlockPos pos, int data, boolean global, CallbackInfo ci) {
        if (eventId == 1032) {
            StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
            Arrays.stream(stackTraceElements)
                    .filter(stackTraceElement -> stackTraceElement.toString().contains("BuildingDimension"))
                    .findAny()
                    .ifPresent(stackTraceElement -> this.eventId = 0);
        }
    }

}
