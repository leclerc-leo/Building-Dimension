package net.fabricmc.BuildingDimension.Events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;

import java.util.*;
import java.util.stream.Stream;

import static net.fabricmc.BuildingDimension.Commands.SyncDimension.*;

public class SyncDimension {

    private final static List<WorldChunk> ChunksToProcess = new ArrayList<>();
    private final static Map<ChunkPos, Set<Vec3i>> PosToProcess = new HashMap<>();
    private static boolean needsProcessing = false;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register((world) -> {
            if (needsSync) {
                Chunk chunk = chunksToSync.poll();

                if (chunk != null) {
                    if (chunk instanceof WorldChunk) {
                        syncChunk((WorldChunk) chunk);
                    }
                }

                if (chunksToSync.isEmpty()) {
                    needsSync = false;
                }

            } else if (needsProcessing) {
                WorldChunk chunk = ChunksToProcess.remove(0);

                if (chunk != null) {
                    postProcess(chunk);
                }

                if (ChunksToProcess.isEmpty()) {
                    needsProcessing = false;
                }
            }
        });
    }

    private static void syncChunk(WorldChunk chunk) {
        int ChunkX = chunk.getPos().x;
        int ChunkZ = chunk.getPos().z;

        WorldChunk creative_chunk = server.getWorld(CREATIVE_OVERWORLD_KEY).getChunk(ChunkX, ChunkZ);
        Stream<BlockPos> streamToCopy = creative_chunk.getLightSourcesStream();
        Set<Vec3i> posToProcess = new HashSet<>();

        streamToCopy.forEach(pos -> {
            posToProcess.add(new Vec3i(pos.getX(), pos.getY(), pos.getZ()));
        });

        for (int y = -63; y < 320; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    BlockState creative_state = creative_chunk.getBlockState(pos);

                    if (state != creative_state) {
                        posToProcess.add(new Vec3i(pos.getX(), pos.getY(), pos.getZ()));
                        creative_chunk.setBlockState(pos, state, false);
                    }
                }
            }
        }

        PosToProcess.put(creative_chunk.getPos(), posToProcess);
        ChunksToProcess.add(creative_chunk);
        needsProcessing = true;
    }

    private static void postProcess(WorldChunk chunk) {
        chunk.runPostProcessing();

        Stream<BlockPos> lightSources = chunk.getLightSourcesStream();
        LightingProvider lightingProvider = chunk.getWorld().getChunkManager().getLightingProvider();

        if (PosToProcess.containsKey(chunk.getPos())) {
            PosToProcess.get(chunk.getPos()).forEach(pos -> lightingProvider.checkBlock(new BlockPos(pos.getX(), pos.getY(), pos.getZ())));
        }

        lightSources.forEach(lightingProvider::checkBlock);
    }
}
