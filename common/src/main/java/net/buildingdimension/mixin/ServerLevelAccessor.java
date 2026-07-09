package net.buildingdimension.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets a building dimension mirror whether its source dimension ticks time (true for the
 * overworld, false for the nether/end and most modded dimensions), matching vanilla's own rule
 * instead of hardcoding it.
 */
@Mixin(ServerLevel.class)
public interface ServerLevelAccessor {

    @Accessor("tickTime")
    boolean buildingDimension$getTickTime();
}
