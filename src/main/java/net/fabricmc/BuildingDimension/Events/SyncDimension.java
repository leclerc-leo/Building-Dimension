package net.fabricmc.BuildingDimension.Events;

import net.fabricmc.BuildingDimension.BuildingDimension;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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

    private static void syncChunk(@NotNull WorldChunk chunk) {
        int ChunkX = chunk.getPos().x;
        int ChunkZ = chunk.getPos().z;

        World creative_world = server.getWorld(CREATIVE_OVERWORLD_KEY);

        if (creative_world == null) {
            BuildingDimension.log("Unable to sync chunk " + ChunkX + ", " + ChunkZ + " because creative world is null");
            return;
        }

        WorldChunk creative_chunk = creative_world.getChunk(ChunkX, ChunkZ);
        Set<Vec3i> posToProcess = new HashSet<>();

        creative_chunk.forEachLightSource((pos, state) ->
            posToProcess.add(new Vec3i(pos.getX(), pos.getY(), pos.getZ()))
        );

        for (int y = -63; y < 320; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    BlockState creative_state = creative_chunk.getBlockState(pos);

                    if (state != creative_state) {
                        posToProcess.add(new Vec3i(pos.getX() + ChunkX * 16, pos.getY(), pos.getZ() + ChunkZ * 16));
                        creative_chunk.setBlockState(pos, state, false);
                    }
                }
            }
        }

        PosToProcess.put(creative_chunk.getPos(), posToProcess);
        ChunksToProcess.add(creative_chunk);
        needsProcessing = true;
    }

    private static void postProcess(@NotNull WorldChunk chunk) {
        chunk.runPostProcessing();

        World world = chunk.getWorld();

        LightingProvider lightingProvider = world.getChunkManager().getLightingProvider();

        if (PosToProcess.containsKey(chunk.getPos())) {
            PosToProcess.get(chunk.getPos()).forEach(pos ->
                lightingProvider.checkBlock(new BlockPos(pos.getX(), pos.getY(), pos.getZ()))
            );
        }

        // sending all block updates with light and block events
        world.getPlayers().forEach(player -> {
            if (player instanceof ServerPlayerEntity) {
                int view_distance = server.getPlayerManager().getViewDistance();

                if (Math.abs(player.getPos().x - chunk.getPos().getStartX()) > 16 * view_distance) return;
                if (Math.abs(player.getPos().z - chunk.getPos().getStartZ()) > 16 * view_distance) return;

                PosToProcess.get(chunk.getPos()).forEach(pos -> {
                    BlockPos update_pos = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
                    ((ServerPlayerEntity) player).networkHandler.sendPacket(new BlockUpdateS2CPacket(chunk.getWorld(), update_pos));
                });
            }
        }
        );
    }
}
