package net.buildingdimension.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.buildingdimension.Constants;
import net.buildingdimension.dimension.BuildingDimensions;
import net.buildingdimension.dimension.ChunkSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * /sync [radius] — copies the chunks around the player from a dimension into its building
 * counterpart (block states, then post-processing and lighting), so the building dimension shows
 * what's actually been built in survival instead of just raw terrain from the shared seed.
 * <p>
 * Always copies source → building, regardless of which of the two the player is standing in when
 * they run it — syncing is one-directional by design, there is no "publish back to survival".
 * The actual copying happens over subsequent ticks via {@link ChunkSync}; this command only
 * resolves the two dimensions and queues the chunks.
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
            source.sendFailure(Component.literal("/sync can only be used by a player"));
            return 0;
        }

        try {
            return sync(player, source.getServer(), radius);
        } catch (Exception e) {
            Constants.LOG.error("Failed to sync chunks for player {}", player.getName().getString(), e);
            source.sendFailure(Component.literal("Failed to sync chunks: " + e.getMessage()));
            return 0;
        }
    }

    private static int sync(ServerPlayer player, MinecraftServer server, int radius) {
        ResourceKey<Level> current = player.level().dimension();
        boolean inBuildingDimension = BuildingDimensions.isBuildingDimension(current);

        Optional<ResourceKey<Level>> counterpart = BuildingDimensions.counterpartOf(server, current);
        if (counterpart.isEmpty()) {
            player.sendSystemMessage(Component.literal("Failed to resolve a counterpart dimension to sync with"));
            return 0;
        }

        ResourceKey<Level> sourceKey = inBuildingDimension ? counterpart.get() : current;
        ResourceKey<Level> buildingKey = inBuildingDimension ? current : counterpart.get();

        if (server.getLevel(sourceKey) == null) {
            player.sendSystemMessage(Component.literal("The source dimension is not loaded"));
            return 0;
        }

        if (server.getLevel(buildingKey) == null) {
            player.sendSystemMessage(Component.literal("The building dimension is not loaded"));
            return 0;
        }

        Vec3 pos = player.position();
        int centerX = SectionPos.posToSectionCoord(pos.x);
        int centerZ = SectionPos.posToSectionCoord(pos.z);

        int queued = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkSync.enqueue(sourceKey, buildingKey, new ChunkPos(centerX + dx, centerZ + dz));
                queued++;
            }
        }

        player.sendOverlayMessage(Component.literal("Syncing " + queued + " chunk" + (queued == 1 ? "" : "s") + " into the building dimension..."));
        return 1;
    }
}
