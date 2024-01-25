package net.fabricmc.BuildingDimension.Commands;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.BuildingDimension.BuildingDimension;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Pair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

public class SyncDimension {

    public static MinecraftServer server;

    /**
     * The chunks that need to be synced
     * <p>
     * see {@link net.fabricmc.BuildingDimension.Events.SyncDimension#init()} for the sync logic
     */
    public static final Queue<Pair<WorldChunk, World>> chunksToSync = new LinkedList<>();

    /**
     * Method to be called to register the command if no radius is specified
     *
     * @param context The command context
     * @return 1 if successful, 0 if not
     */
    public static int sync_chunk_one(CommandContext<ServerCommandSource> context) {
        return sync(context, 1);
    }

    /**
     * Method to be called to register the command if a radius is specified
     *
     * @param context The command context
     * @return 1 if successful, 0 if not
     */
    public static int sync_chunk_radius(CommandContext<ServerCommandSource> context) {
        return sync(context, context.getArgument("radius", Integer.class));
    }

    /**
     * Marks the chunks in the radius around the player to be synced
     * see {@link net.fabricmc.BuildingDimension.Events.SyncDimension#init()} for the sync logic
     *
     * @param context The command context
     * @param radius The radius around the player to sync
     * @return 1 if successful, 0 if not
     */
    private static int sync(CommandContext<ServerCommandSource> context, int radius) {
        try {
            if ( server == null ) server = Objects.requireNonNull(context.getSource().getServer());

            BuildingDimension.log("Syncing chunks in radius " + radius + " around " + context.getSource().getPosition().toString());

            ServerWorld world;
            ServerWorld building_world;

            if (context.getSource().getWorld().getRegistryKey().getValue().getNamespace().equals(BuildingDimension.MOD_ID)){
                world = context.getSource().getServer().getWorld(
                        SwitchDimension.DIMENSIONS.get(context.getSource().getWorld().getRegistryKey())
                );
                building_world = context.getSource().getWorld();
            } else {
                world = context.getSource().getWorld();
                building_world = context.getSource().getServer().getWorld(
                        SwitchDimension.DIMENSIONS.get(context.getSource().getWorld().getRegistryKey())
                );
            }

            if (world == null) {
                BuildingDimension.log("Failed to sync chunks: unable to find world for which to sync chunks from");
                return -1;
            }

            if (building_world == null) {
                BuildingDimension.log("Failed to sync chunks: unable to find world for which to sync chunks to");
                return -1;
            }

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int chunkX = (int) Math.floor(context.getSource().getPosition().x / 16) + x;
                    int chunkZ = (int) Math.floor(context.getSource().getPosition().z / 16) + z;

                    WorldChunk chunk = world.getChunk(chunkX, chunkZ);
                    chunksToSync.add(new Pair<>(chunk, building_world));
                }
            }

            return 0;
        } catch (Exception e) {
            BuildingDimension.logError("Failed to sync chunks: ", e, context.getSource());
            return -1;
        }
    }
}
