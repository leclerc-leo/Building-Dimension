package net.buildingdimension.command;

import net.buildingdimension.config.BuildingDimensionConfig;
import net.buildingdimension.config.SwitchWhitelist;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * Whether a player is allowed to enter a building dimension via {@code /switch}, per
 * {@link BuildingDimensionConfig#switchRequireOp()} and {@link BuildingDimensionConfig#switchRequireWhitelist()}
 * (both, if both are enabled — a player needs to satisfy every check that's turned on).
 * <p>
 * Only gates the "real dimension -> building dimension" direction; a player already inside a
 * building dimension can always leave (see {@code SwitchCommand}), so nobody gets stuck.
 */
public final class SwitchAccess {

    public enum Result {
        /** Passes every enabled check: full creative access. */
        ALLOWED,
        /** Fails a check, but {@link BuildingDimensionConfig#switchDeniedPlayersUseSpectator()} lets them look. */
        DENIED_SPECTATOR,
        /** Fails a check, and spectator fallback is off: {@code /switch} refuses outright. */
        DENIED
    }

    private SwitchAccess() {
    }

    public static Result check(ServerPlayer player, CommandSourceStack source) {
        boolean opSatisfied = !BuildingDimensionConfig.switchRequireOp()
            || source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        boolean whitelistSatisfied = !BuildingDimensionConfig.switchRequireWhitelist()
            || SwitchWhitelist.contains(player.getGameProfile().name());

        if (opSatisfied && whitelistSatisfied) {
            return Result.ALLOWED;
        }
        return BuildingDimensionConfig.switchDeniedPlayersUseSpectator() ? Result.DENIED_SPECTATOR : Result.DENIED;
    }
}
