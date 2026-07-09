package net.buildingdimension.mixin;

import net.buildingdimension.dimension.BuildingDimensions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla portal destinations are hardcoded to the real overworld/nether/end
 * ({@code NetherPortalBlock}/{@code EndPortalBlock}#getPortalDestination pick {@code Level.NETHER}/
 * {@code Level.OVERWORLD}/{@code Level.END} outright, not a dimension relative to where the portal
 * was built). Left alone, a nether portal or end portal built inside a building dimension would
 * carry the player straight into the real nether/end, bypassing {@code /switch} — and whatever
 * they were holding — entirely. {@link Entity#canTeleport(Level, Level)} is the single choke point
 * every portal implementation (nether portal, end portal, end gateway) funnels through before
 * committing to a cross-dimension teleport, so blocking it there covers all of them at once.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "canTeleport", at = @At("HEAD"), cancellable = true)
    private void buildingDimension$blockPortalEscape(Level from, Level to, CallbackInfoReturnable<Boolean> cir) {
        if (BuildingDimensions.isBuildingDimension(from.dimension()) || BuildingDimensions.isBuildingDimension(to.dimension())) {
            cir.setReturnValue(false);
        }
    }
}
