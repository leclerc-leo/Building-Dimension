package net.buildingdimension.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.buildingdimension.Constants;
import net.buildingdimension.config.BuildingDimensionConfig;
import net.buildingdimension.dimension.BuildingDimensions;
import net.buildingdimension.dimension.ChunkSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * /sync [radius] — copies the chunks around the player from a dimension into its building
 * counterpart (block states, block entities, entities, then post-processing and lighting), so the
 * building dimension shows what's actually been built in survival instead of just raw terrain from
 * the shared seed.
 * <p>
 * Always copies source → building, regardless of which of the two the player is standing in when
 * they run it — syncing is one-directional by design, there is no "publish back to survival".
 * The actual copying happens over subsequent ticks via {@link ChunkSync}; this command only
 * resolves the two dimensions and queues the chunks.
 * <p>
 * A non-operator's radius is capped at {@link BuildingDimensionConfig#syncMaxRadius()} — left
 * uncapped, {@code /sync} would let any player queue an arbitrarily large number of chunk
 * generations at once, which can fill the disk and stall the chunk system for everyone.
 * Operators are never capped.
 */
public class SyncCommand {

    private static final int DEFAULT_RADIUS = 3;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("sync")
                .executes(context -> sync(context, DEFAULT_RADIUS))
                .then(Commands.argument("radius", IntegerArgumentType.integer(0))
                    .executes(context -> sync(context, IntegerArgumentType.getInteger(context, "radius"))))
        );
    }

    private static int sync(CommandContext<CommandSourceStack> context, int radius) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.translatable("commands.building_dimension.sync.player_only"));
            return 0;
        }

        try {
            return sync(player, source, radius);
        } catch (Exception e) {
            Constants.LOG.error("Failed to sync chunks for player {}", player.getName().getString(), e);
            source.sendFailure(Component.translatable("commands.building_dimension.sync.failed", e.getMessage()));
            return 0;
        }
    }

    private static int sync(ServerPlayer player, CommandSourceStack source, int radius) {
        MinecraftServer server = source.getServer();
        int maxRadius = BuildingDimensionConfig.syncMaxRadius();
        boolean isOp = source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        int effectiveRadius = radius;
        if (!isOp && effectiveRadius > maxRadius) {
            effectiveRadius = maxRadius;
            player.sendSystemMessage(Component.translatable("commands.building_dimension.sync.radius_capped", maxRadius));
        }

        ResourceKey<Level> current = player.level().dimension();
        boolean inBuildingDimension = BuildingDimensions.isBuildingDimension(current);

        Optional<ResourceKey<Level>> counterpart = BuildingDimensions.counterpartOf(server, current);
        if (counterpart.isEmpty()) {
            player.sendSystemMessage(Component.translatable("commands.building_dimension.sync.no_counterpart"));
            return 0;
        }

        ResourceKey<Level> sourceKey = inBuildingDimension ? counterpart.get() : current;
        ResourceKey<Level> buildingKey = inBuildingDimension ? current : counterpart.get();

        if (server.getLevel(sourceKey) == null) {
            player.sendSystemMessage(Component.translatable("commands.building_dimension.sync.source_not_loaded"));
            return 0;
        }

        if (server.getLevel(buildingKey) == null) {
            player.sendSystemMessage(Component.translatable("commands.building_dimension.sync.building_not_loaded"));
            return 0;
        }

        Vec3 pos = player.position();
        int centerX = SectionPos.posToSectionCoord(pos.x);
        int centerZ = SectionPos.posToSectionCoord(pos.z);

        int queued = 0;
        for (int dx = -effectiveRadius; dx <= effectiveRadius; dx++) {
            for (int dz = -effectiveRadius; dz <= effectiveRadius; dz++) {
                ChunkSync.enqueue(player.getUUID(), sourceKey, buildingKey, new ChunkPos(centerX + dx, centerZ + dz));
                queued++;
            }
        }

        player.sendOverlayMessage(Component.translatable("commands.building_dimension.sync.queued", queued));
        return 1;
    }
}
