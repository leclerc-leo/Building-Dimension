package net.buildingdimension.dimension;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

/**
 * Copies chunks from a source dimension into its building counterpart. Building dimensions are
 * generated fresh from the source's own seed and chunk generator ({@link DimensionFactory}), so
 * without this the building dimension only ever shows raw terrain, never what's actually been
 * built in survival.
 * <p>
 * Chunk loading is requested via {@link net.minecraft.server.level.ServerChunkCache#addTicketAndLoadWithRadius},
 * which only registers a ticket and returns a future — unlike {@code Level#getChunk(x, z)}, it
 * does not block the calling thread while the chunk generates. That distinction is the whole
 * point: a chunk in the building dimension is very often being visited for the first time, so
 * forcing it to fully generate synchronously on the main thread (as an earlier version of this
 * class did) visibly stalled the server on anything beyond a small radius. Letting Minecraft's
 * own chunk worker threads do that work, and only touching the main thread once a chunk is
 * actually ready, keeps a large {@code /sync} radius from ever blocking a tick.
 */
public final class ChunkSync {

    private record Job(ResourceKey<Level> source, ResourceKey<Level> building, ChunkPos pos) {
    }

    private record ReadyJob(ServerLevel buildingLevel, ChunkPos pos, LevelChunk sourceChunk, LevelChunk buildingChunk) {
    }

    private static final Queue<Job> PENDING = new ArrayDeque<>();
    private static final Queue<ReadyJob> READY = new ArrayDeque<>();

    // Bounds how many chunk-pairs we ask the chunk system to load at once. Loading itself happens
    // off the main thread, so this isn't a "don't block the tick" limit like it would be for
    // synchronous work — it's just to avoid dumping an entire radius=15 sync's worth of tickets
    // (900+) on the chunk system in a single burst.
    private static final int MAX_NEW_JOBS_PER_TICK = 16;
    private static final int MAX_IN_FLIGHT = 64;
    private static int inFlight = 0;

    // This one DOES bound main-thread time: when chunks are already loaded (e.g. re-syncing the
    // same area), the load future above resolves near-instantly, so without this every one of the
    // up-to-64 in-flight jobs would run its (non-trivial — postProcessGeneration plus thousands of
    // checkBlock calls) copy+relight work back-to-back in the same tick.
    private static final int READY_JOBS_PER_TICK = 4;

    private ChunkSync() {
    }

    /**
     * Queues a chunk to be copied from {@code source} into {@code building}.
     */
    public static void enqueue(ResourceKey<Level> source, ResourceKey<Level> building, ChunkPos pos) {
        PENDING.add(new Job(source, building, pos));
    }

    /**
     * Starts a small batch of new loads, then processes a small batch of chunks that have already
     * finished loading. Meant to be called once per server tick.
     */
    public static void tick(MinecraftServer server) {
        for (int i = 0; i < MAX_NEW_JOBS_PER_TICK && inFlight < MAX_IN_FLIGHT; i++) {
            Job job = PENDING.poll();
            if (job == null) {
                break;
            }
            inFlight++;
            startLoad(server, job);
        }

        for (int i = 0; i < READY_JOBS_PER_TICK; i++) {
            ReadyJob ready = READY.poll();
            if (ready == null) {
                break;
            }
            copyBlocks(ready.sourceChunk(), ready.buildingChunk());
            relight(server, ready.buildingLevel(), ready.pos(), ready.buildingChunk());
        }
    }

    private static void startLoad(MinecraftServer server, Job job) {
        ServerLevel sourceLevel = server.getLevel(job.source());
        ServerLevel buildingLevel = server.getLevel(job.building());
        if (sourceLevel == null || buildingLevel == null) {
            inFlight--;
            return;
        }

        CompletableFuture<?> sourceFuture = sourceLevel.getChunkSource().addTicketAndLoadWithRadius(TicketType.PORTAL, job.pos(), 0);
        CompletableFuture<?> buildingFuture = buildingLevel.getChunkSource().addTicketAndLoadWithRadius(TicketType.PORTAL, job.pos(), 0);

        CompletableFuture.allOf(sourceFuture, buildingFuture).thenRunAsync(() -> {
            try {
                LevelChunk sourceChunk = sourceLevel.getChunkSource().getChunkNow(job.pos().x(), job.pos().z());
                LevelChunk buildingChunk = buildingLevel.getChunkSource().getChunkNow(job.pos().x(), job.pos().z());
                if (sourceChunk != null && buildingChunk != null) {
                    READY.add(new ReadyJob(buildingLevel, job.pos(), sourceChunk, buildingChunk));
                }
            } finally {
                inFlight--;
            }
        }, server);
    }

    private static void copyBlocks(LevelChunk sourceChunk, LevelChunk buildingChunk) {
        LevelChunkSection[] sourceSections = sourceChunk.getSections();
        LevelChunkSection[] buildingSections = buildingChunk.getSections();

        for (int i = 0; i < sourceSections.length && i < buildingSections.length; i++) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                sourceSections[i].getStates().write(buffer);
                buildingSections[i].getStates().read(buffer);
            } finally {
                buffer.release();
            }
            buildingSections[i].recalcBlockCounts();
        }

        for (Heightmap.Types type : ChunkStatus.FULL.heightmapsAfter()) {
            buildingChunk.setHeightmap(type, sourceChunk.getOrCreateHeightmapUnprimed(type).getRawData());
        }

        buildingChunk.markUnsaved();
    }

    private static void relight(MinecraftServer server, ServerLevel buildingLevel, ChunkPos pos, LevelChunk chunk) {
        chunk.postProcessGeneration(buildingLevel);
        chunk.markUnsaved();
        chunk.initializeLightSources();
        ThreadedLevelLightEngine lightEngine = buildingLevel.getChunkSource().getLightEngine();
        lightEngine.initializeLight(chunk, false);
        lightEngine.lightChunk(chunk, false)
            .thenRunAsync(() -> resendToTrackingPlayers(buildingLevel, chunk, lightEngine), server);
    }

    private static void resendToTrackingPlayers(ServerLevel level, LevelChunk chunk, ThreadedLevelLightEngine lightEngine) {
        ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(chunk, lightEngine, null, null);
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false)) {
            player.connection.send(packet);
        }
    }
}
