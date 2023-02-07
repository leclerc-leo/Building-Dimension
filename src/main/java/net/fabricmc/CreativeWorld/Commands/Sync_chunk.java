package net.fabricmc.CreativeWorld.Commands;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.CreativeWorld.CreativeWorld;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.Position;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class Sync_chunk {

    public static RegistryKey<World> CREATIVE_OVERWORLD_KEY = CreativeWorld.OVERWORLD_WORLD_KEY;

    public static int sync_chunk(CommandContext<ServerCommandSource> context) {
        World world = context.getSource().getWorld();
        Position position = context.getSource().getPosition();
        Chunk chunk;

        if (world.getRegistryKey() == World.OVERWORLD) {

            int x = (int) (position.getX() / 16);
            int z = (int) (position.getZ() / 16);
            chunk = world.getChunk(x , z);

        } else if (world.getRegistryKey() == CREATIVE_OVERWORLD_KEY) {

            int x = (int) (position.getX() / 16);
            int z = (int) (position.getZ() / 16);
            World overworld = context.getSource().getServer().getWorld(World.OVERWORLD);

            if (overworld == null) {
                return -1;
            }

            chunk = overworld.getChunk(x , z);

        } else {

            return -1;
        }

        // we want to sync this chunk to the creative world by putting the chunk in the creative world

        World creative_world = context.getSource().getServer().getWorld(CREATIVE_OVERWORLD_KEY);

        if (creative_world == null) {
            return -1;
        }

        return 0;
    }
}
