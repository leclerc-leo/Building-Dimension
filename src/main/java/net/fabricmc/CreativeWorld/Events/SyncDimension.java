package net.fabricmc.CreativeWorld.Events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import static net.fabricmc.CreativeWorld.Commands.Sync_chunk.*;

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

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 256; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    creative_chunk.setBlockState(pos, chunk.getBlockState(pos), false);
                }
            }
        }
    }
}
