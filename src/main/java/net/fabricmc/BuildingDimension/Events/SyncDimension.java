package net.fabricmc.BuildingDimension.Events;

import io.netty.buffer.ByteBufAllocator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
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
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            if (needsSync) {
                Pair<WorldChunk, World> pair = chunksToSync.remove();
                WorldChunk chunk = pair.getLeft();

                if (chunk != null) {
                    syncChunk(chunk, pair.getRight());
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

    private static void syncChunk(@NotNull WorldChunk chunk, @NotNull World creative_world) {
        int ChunkX = chunk.getPos().x;
        int ChunkZ = chunk.getPos().z;

        WorldChunk creative_chunk = creative_world.getChunk(ChunkX, ChunkZ);
        Set<Vec3i> posToProcess = new HashSet<>();

        creative_chunk.forEachLightSource((pos, state) ->
            posToProcess.add(new Vec3i(pos.getX(), pos.getY(), pos.getZ()))
        );

        int n_sections = chunk.getSectionArray().length;

        for (int y_section = 0; y_section < n_sections; y_section++) {
            ChunkSection section = chunk.getSectionArray()[y_section],
                    creative_section = creative_chunk.getSectionArray()[y_section];

            PacketByteBuf buf = new PacketByteBuf(
                    ByteBufAllocator.DEFAULT.buffer()
            );

            section.getBlockStateContainer().writePacket(buf);

            creative_section.getBlockStateContainer().readPacket(buf);
        }

        creative_chunk.forEachLightSource((pos, state) ->
            posToProcess.add(new Vec3i(pos.getX(), pos.getY(), pos.getZ()))
        );

        PosToProcess.put(creative_chunk.getPos(), posToProcess);
        ChunksToProcess.add(creative_chunk);
        needsProcessing = true;
    }

    private static void postProcess(@NotNull WorldChunk chunk) {
        chunk.runPostProcessing();

        World world = chunk.getWorld();

        LightingProvider lightingProvider = world.getLightingProvider();
        for (Vec3i pos : PosToProcess.get(chunk.getPos())) {
            lightingProvider.checkBlock(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        }

        world.getPlayers().forEach(player -> {
            if (player instanceof ServerPlayerEntity) {
                int view_distance = server.getPlayerManager().getViewDistance();

                if (Math.abs(player.getPos().x - chunk.getPos().getStartX()) > 16 * view_distance) return;
                if (Math.abs(player.getPos().z - chunk.getPos().getStartZ()) > 16 * view_distance) return;

                ((ServerPlayerEntity) player).networkHandler.sendPacket(new ChunkDataS2CPacket(
                        chunk,
                        chunk.getWorld().getLightingProvider(),
                        new BitSet(65536),
                        new BitSet(65536)
                ));
            }
        });
    }
}
