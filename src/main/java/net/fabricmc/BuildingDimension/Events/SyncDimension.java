package net.fabricmc.BuildingDimension.Events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import static net.fabricmc.BuildingDimension.Commands.SyncDimension.*;

public class SyncDimension {

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register((world) -> {
            if (needsSync) {
                Chunk chunk = chunksToSync.poll();

                if (chunk != null) {
                    syncChunk(chunk);
                }

                if (chunksToSync.isEmpty()) {
                    needsSync = false;
                }
            }
        });
    }

    private static void syncChunk(Chunk chunk) {
        int ChunkX = chunk.getPos().x;
        int ChunkZ = chunk.getPos().z;

        WorldChunk creative_chunk = server.getWorld(CREATIVE_OVERWORLD_KEY).getChunk(ChunkX, ChunkZ);

        for (int y = -63; y < 320; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    BlockState creative_state = creative_chunk.getBlockState(pos);

                    if (state != creative_state) {
                        creative_chunk.setBlockState(pos, state, false);
                    }
                }
            }
        }
    }
}
