package net.buildingdimension.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.buildingdimension.Constants;
import net.buildingdimension.dimension.BuildingDimensions;
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
 * wiped, their previous creative state (if any) is restored, and they end up in creative mode at
 * the same coordinates. Leaving: the reverse, back at the exact spot they switched from.
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
            source.sendFailure(Component.literal("/switch can only be used by a player"));
            return 0;
        }

        if (isOnCooldown(player)) {
            player.sendOverlayMessage(Component.literal("You must wait a few seconds between dimension switches"));
            return 0;
        }

        try {
            return switchPlayer(player, source.getServer());
        } catch (Exception e) {
            Constants.LOG.error("Failed to switch dimension for player {}", player.getName().getString(), e);
            source.sendFailure(Component.literal("Failed to switch dimension: " + e.getMessage()));
            return 0;
        }
    }

    private static int switchPlayer(ServerPlayer player, MinecraftServer server) {
        ResourceKey<Level> from = player.level().dimension();
        Optional<ResourceKey<Level>> counterpart = BuildingDimensions.counterpartOf(server, from);

        if (counterpart.isEmpty()) {
            player.sendSystemMessage(Component.literal("Failed to resolve a counterpart dimension"));
            return 0;
        }

        ServerLevel target = server.getLevel(counterpart.get());
        if (target == null) {
            player.sendSystemMessage(Component.literal("The target dimension is not loaded"));
            return 0;
        }

        SwitchDataStore store = SwitchDataStore.get(server);

        if (BuildingDimensions.isBuildingDimension(from)) {
            returnToSource(player, store, target);
        } else {
            enterBuildingDimension(player, store, target);
        }

        LAST_SWITCH.put(player.getUUID(), System.currentTimeMillis());
        return 1;
    }

    private static void enterBuildingDimension(ServerPlayer player, SwitchDataStore store, ServerLevel target) {
        store.setSourceSnapshot(player.getUUID(), PlayerSnapshot.capture(player));

        PlayerSnapshot.clean(player);

        Vec3 pos = player.position();
        teleport(player, target, pos, player.getYRot(), player.getXRot());

        store.of(player.getUUID()).building().ifPresent(snapshot -> snapshot.restore(player));
        player.setGameMode(GameType.CREATIVE);
    }

    private static void returnToSource(ServerPlayer player, SwitchDataStore store, ServerLevel target) {
        store.setBuildingSnapshot(player.getUUID(), PlayerSnapshot.capture(player));

        PlayerSnapshot.clean(player);

        Optional<PlayerSnapshot> source = store.of(player.getUUID()).source();

        Vec3 pos = source.map(PlayerSnapshot::position).orElse(player.position());
        float yRot = source.map(PlayerSnapshot::yRot).orElse(player.getYRot());
        float xRot = source.map(PlayerSnapshot::xRot).orElse(player.getXRot());

        teleport(player, target, pos, yRot, xRot);

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
}
