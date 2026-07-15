package net.buildingdimension.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.buildingdimension.Constants;
import net.buildingdimension.dimension.BuildingDimensions;
import net.buildingdimension.dimension.DimensionFactory;
import net.buildingdimension.persistence.PlayerSnapshot;
import net.buildingdimension.persistence.SwitchDataStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * /switch — teleports the player between a dimension and its creative building counterpart.
 * <p>
 * Entering a building dimension: the player's real state is snapshotted and stored, the player is
 * wiped, their previous creative state (if any) is restored, and they end up in creative mode (or
 * spectator, see {@link SwitchAccess}) at the same coordinates. Leaving: the reverse, back at the
 * exact spot they switched from.
 * <p>
 * Capturing a snapshot never commits it to {@link SwitchDataStore} until after the teleport has
 * actually succeeded — {@link ServerPlayer#teleportTo} can throw (a misbehaving mixin from another
 * mod, a corrupt chunk, etc.), and committing first would otherwise leave the player wiped with no
 * way back, and would go on to silently overwrite their last-good snapshot the next time they tried.
 */
public class SwitchCommand {

    private static final long COOLDOWN_MS = 5000;
    private static final Map<UUID, Long> LAST_SWITCH = new HashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("switch")
                .executes(SwitchCommand::run)
        );
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.translatable("commands.building_dimension.switch.player_only"));
            return 0;
        }

        if (isOnCooldown(player)) {
            player.sendOverlayMessage(Component.translatable("commands.building_dimension.switch.cooldown"));
            return 0;
        }

        try {
            return switchPlayer(player, source);
        } catch (Exception e) {
            Constants.LOG.error("Failed to switch dimension for player {}", player.getName().getString(), e);
            source.sendFailure(Component.translatable("commands.building_dimension.switch.failed", e.getMessage()));
            return 0;
        }
    }

    private static int switchPlayer(ServerPlayer player, CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ResourceKey<Level> from = player.level().dimension();
        SwitchDataStore store = SwitchDataStore.get(server);

        boolean success = BuildingDimensions.isBuildingDimension(from)
            ? tryReturnToSource(player, server, store, from)
            : tryEnterBuildingDimension(player, source, server, store, from);

        if (!success) {
            return 0;
        }

        LAST_SWITCH.put(player.getUUID(), System.currentTimeMillis());
        return 1;
    }

    private static boolean tryEnterBuildingDimension(
        ServerPlayer player, CommandSourceStack source, MinecraftServer server, SwitchDataStore store, ResourceKey<Level> from
    ) {
        SwitchAccess.Result access = SwitchAccess.check(player, source);
        if (access == SwitchAccess.Result.DENIED) {
            player.sendSystemMessage(Component.translatable("commands.building_dimension.switch.denied"));
            return false;
        }

        ServerLevel sourceLevel = server.getLevel(from);
        if (sourceLevel == null) {
            player.sendSystemMessage(Component.translatable("commands.building_dimension.switch.no_counterpart"));
            return false;
        }

        ResourceKey<Level> counterpart = DimensionFactory.getOrCreate(server, sourceLevel);
        ServerLevel target = server.getLevel(counterpart);
        if (target == null) {
            player.sendSystemMessage(Component.translatable("commands.building_dimension.switch.target_not_loaded"));
            return false;
        }

        enterBuildingDimension(player, store, target, access == SwitchAccess.Result.DENIED_SPECTATOR);
        return true;
    }

    private static boolean tryReturnToSource(ServerPlayer player, MinecraftServer server, SwitchDataStore store, ResourceKey<Level> from) {
        Optional<PlayerSnapshot> sourceSnapshot = store.of(player.getUUID()).source();
        ResourceKey<Level> targetKey = sourceSnapshot
            .map(PlayerSnapshot::dimension)
            .or(() -> BuildingDimensions.sourceOf(server, from))
            .orElse(null);

        if (targetKey == null) {
            player.sendSystemMessage(Component.translatable("commands.building_dimension.switch.no_counterpart"));
            return false;
        }

        ServerLevel target = server.getLevel(targetKey);
        if (target == null) {
            player.sendSystemMessage(Component.translatable("commands.building_dimension.switch.target_not_loaded"));
            return false;
        }

        returnToSource(player, store, target, sourceSnapshot);
        return true;
    }

    private static void enterBuildingDimension(ServerPlayer player, SwitchDataStore store, ServerLevel target, boolean spectatorOnly) {
        PlayerSnapshot sourceSnapshot = PlayerSnapshot.capture(player);
        Vec3 pos = player.position();

        teleport(player, target, pos, player.getYRot(), player.getXRot());

        store.setSourceSnapshot(player.getUUID(), sourceSnapshot);
        PlayerSnapshot.clean(player);

        store.of(player.getUUID()).building().ifPresent(snapshot -> snapshot.restore(player));
        player.setGameMode(spectatorOnly ? GameType.SPECTATOR : GameType.CREATIVE);
    }

    private static void returnToSource(ServerPlayer player, SwitchDataStore store, ServerLevel target, Optional<PlayerSnapshot> source) {
        PlayerSnapshot buildingSnapshot = PlayerSnapshot.capture(player);

        Vec3 pos = source.map(PlayerSnapshot::position).orElse(player.position());
        float yRot = source.map(PlayerSnapshot::yRot).orElse(player.getYRot());
        float xRot = source.map(PlayerSnapshot::xRot).orElse(player.getXRot());

        teleport(player, target, pos, yRot, xRot);

        store.setBuildingSnapshot(player.getUUID(), buildingSnapshot);
        PlayerSnapshot.clean(player);

        source.ifPresentOrElse(
            snapshot -> snapshot.restore(player),
            () -> player.setGameMode(GameType.SURVIVAL)
        );
    }

    private static void teleport(ServerPlayer player, ServerLevel target, Vec3 pos, float yRot, float xRot) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        player.teleportTo(target, pos.x, pos.y, pos.z, Set.<Relative>of(), yRot, xRot, false);

        target.playSound(null, pos.x, pos.y, pos.z,
            SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static boolean isOnCooldown(ServerPlayer player) {
        Long lastSwitch = LAST_SWITCH.get(player.getUUID());
        return lastSwitch != null && System.currentTimeMillis() - lastSwitch < COOLDOWN_MS;
    }

    /**
     * Drops every player's /switch cooldown. Called on server stop, since the cooldown map is
     * static and would otherwise leak stale entries across a singleplayer world switch.
     */
    public static void clearCooldowns() {
        LAST_SWITCH.clear();
    }
}
