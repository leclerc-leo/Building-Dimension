package net.fabricmc.BuildingDimension.Commands;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.BuildingDimension.BuildingDimension;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

public class SyncDimension {

    public static MinecraftServer server;

    public static boolean needsSync = false;

    public static final Queue<Chunk> chunksToSync = new LinkedList<>();

    public static int sync_chunk_one(CommandContext<ServerCommandSource> context) {
        return sync(context, 1);
    }

    public static int sync_chunk_radius(CommandContext<ServerCommandSource> context) {
        return sync(context, context.getArgument("radius", Integer.class));
    }

    private static int sync(CommandContext<ServerCommandSource> context, int radius) {
        if ( server == null ) server = Objects.requireNonNull(context.getSource().getServer());

        BuildingDimension.log("Syncing chunks in radius " + radius + " around " + context.getSource().getPosition().toString());

        World world = context.getSource().getServer().getWorld(World.OVERWORLD);

        if (world == null) {
            BuildingDimension.log("Failed to sync chunks: world is null when getting chunks");
            return -1;
        }

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int chunkX = (int) Math.floor(context.getSource().getPosition().x / 16) + x;
                int chunkZ = (int) Math.floor(context.getSource().getPosition().z / 16) + z;

                Chunk chunk = world.getChunk(chunkX, chunkZ);
                chunksToSync.add(chunk);
            }
        }

        needsSync = true;

        return 0;
    }
}
