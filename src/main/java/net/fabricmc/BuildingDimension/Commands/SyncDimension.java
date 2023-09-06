package net.fabricmc.BuildingDimension.Commands;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.BuildingDimension.BuildingDimension;
import net.minecraft.registry.RegistryKey;
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

    public static final RegistryKey<World> CREATIVE_OVERWORLD_KEY = BuildingDimension.OVERWORLD_WORLD_KEY;

    public static int sync_chunk_one(CommandContext<ServerCommandSource> context) {

        if ( server == null ) server = Objects.requireNonNull(context.getSource().getServer());

        BuildingDimension.log("Syncing chunk at " + context.getSource().getPosition().toString());

        int chunkX = (int) Math.floor(context.getSource().getPosition().x / 16);
        int chunkZ = (int) Math.floor(context.getSource().getPosition().z / 16);

        Chunk chunk = context.getSource().getWorld().getChunk(chunkX, chunkZ);
        chunksToSync.add(chunk);

        needsSync = true;

        return 0;
    }

    public static int sync_chunk_radius(CommandContext<ServerCommandSource> context) {

        if ( server == null ) server = Objects.requireNonNull(context.getSource().getServer());
        int radius = context.getArgument("radius", Integer.class);

        BuildingDimension.log("Syncing chunks in radius " + radius + " around " + context.getSource().getPosition().toString());

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int chunkX = (int) Math.floor(context.getSource().getPosition().x / 16) + x;
                int chunkZ = (int) Math.floor(context.getSource().getPosition().z / 16) + z;

                Chunk chunk = context.getSource().getWorld().getChunk(chunkX, chunkZ);
                chunksToSync.add(chunk);
            }
        }

        needsSync = true;

        return 0;
    }
}
