package net.buildingdimension.dimension;

import io.netty.buffer.Unpooled;
import net.buildingdimension.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
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

    private record Job(UUID requester, ResourceKey<Level> source, ResourceKey<Level> building, ChunkPos pos) {
    }

    private record ReadyJob(UUID requester, ServerLevel buildingLevel, ChunkPos pos, LevelChunk sourceChunk, LevelChunk buildingChunk) {
    }

    private static final Queue<Job> PENDING = new ArrayDeque<>();
    private static final Queue<ReadyJob> READY = new ArrayDeque<>();

    /** [total queued, completed so far], per requesting player, so a completion message can be sent. */
    private static final Map<UUID, int[]> PROGRESS = new HashMap<>();

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
     * Queues a chunk to be copied from {@code source} into {@code building}, on behalf of
     * {@code requester} (used only to report progress back to them once every chunk they queued has
     * been processed).
     */
    public static void enqueue(UUID requester, ResourceKey<Level> source, ResourceKey<Level> building, ChunkPos pos) {
        PENDING.add(new Job(requester, source, building, pos));
        PROGRESS.computeIfAbsent(requester, id -> new int[2])[0]++;
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
            copyBlockEntities(ready.sourceChunk(), ready.buildingChunk());
            copyEntities((ServerLevel) ready.sourceChunk().getLevel(), ready.buildingLevel(), ready.pos());
            relight(server, ready.buildingLevel(), ready.pos(), ready.buildingChunk());
            completeOne(server, ready.requester());
        }
    }

    private static void startLoad(MinecraftServer server, Job job) {
        ServerLevel sourceLevel = server.getLevel(job.source());
        ServerLevel buildingLevel = server.getLevel(job.building());
        if (sourceLevel == null || buildingLevel == null) {
            inFlight--;
            completeOne(server, job.requester());
            return;
        }

        CompletableFuture<?> sourceFuture = sourceLevel.getChunkSource().addTicketAndLoadWithRadius(TicketType.PORTAL, job.pos(), 0);
        CompletableFuture<?> buildingFuture = buildingLevel.getChunkSource().addTicketAndLoadWithRadius(TicketType.PORTAL, job.pos(), 0);

        CompletableFuture.allOf(sourceFuture, buildingFuture).whenCompleteAsync((ignored, throwable) -> {
            try {
                if (throwable != null) {
                    Constants.LOG.warn("Failed to load chunk {} for /sync", job.pos(), throwable);
                    completeOne(server, job.requester());
                    return;
                }

                LevelChunk sourceChunk = sourceLevel.getChunkSource().getChunkNow(job.pos().x(), job.pos().z());
                LevelChunk buildingChunk = buildingLevel.getChunkSource().getChunkNow(job.pos().x(), job.pos().z());
                if (sourceChunk != null && buildingChunk != null) {
                    READY.add(new ReadyJob(job.requester(), buildingLevel, job.pos(), sourceChunk, buildingChunk));
                } else {
                    completeOne(server, job.requester());
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

    /**
     * {@link #copyBlocks} only moves raw block-state palettes, which vanilla's own block-entity
     * placement logic never runs over — so chests, signs, and every other block backed by a
     * {@link BlockEntity} would otherwise copy as an inert block with no data behind it. Clears
     * whatever block entities the building chunk already had (a previous /sync, or leftover from
     * building there directly) before re-creating one for every block entity the source chunk has,
     * from a full NBT round-trip of the source's data.
     */
    private static void copyBlockEntities(LevelChunk sourceChunk, LevelChunk buildingChunk) {
        for (BlockPos pos : List.copyOf(buildingChunk.getBlockEntities().keySet())) {
            buildingChunk.removeBlockEntity(pos);
        }

        ServerLevel sourceLevel = (ServerLevel) sourceChunk.getLevel();
        ServerLevel buildingLevel = (ServerLevel) buildingChunk.getLevel();

        for (BlockEntity sourceBlockEntity : List.copyOf(sourceChunk.getBlockEntities().values())) {
            CompoundTag tag = sourceBlockEntity.saveWithFullMetadata(sourceLevel.registryAccess());
            BlockPos pos = sourceBlockEntity.getBlockPos();
            BlockEntity copy = BlockEntity.loadStatic(pos, buildingChunk.getBlockState(pos), tag, buildingLevel.registryAccess());
            if (copy != null) {
                copy.setLevel(buildingLevel);
                buildingChunk.addAndRegisterBlockEntity(copy);
            }
        }
    }

    /**
     * Item frames, armor stands, paintings, and the display/marker entities many mods use to build
     * "fake blocks" all live outside the block-state/block-entity model entirely — they're regular
     * {@link Entity} instances, tracked by the level rather than the chunk. Discards whatever
     * non-player entities the building dimension's chunk column already has, then clones every
     * non-player entity from the same column in the source (via a full NBT save/load round-trip,
     * the same mechanism vanilla uses to persist entities to disk) so decorative or fake-block setups
     * actually show up.
     */
    private static void copyEntities(ServerLevel sourceLevel, ServerLevel buildingLevel, ChunkPos pos) {
        if (sourceLevel == null) {
            return;
        }

        AABB bounds = new AABB(
            pos.getMinBlockX(), buildingLevel.getMinY(), pos.getMinBlockZ(),
            pos.getMaxBlockX() + 1.0, buildingLevel.getMaxY() + 1.0, pos.getMaxBlockZ() + 1.0
        );

        for (Entity existing : buildingLevel.getEntities((Entity) null, bounds, ChunkSync::isColumnEntity)) {
            if (ChunkPos.containing(existing.blockPosition()).equals(pos)) {
                existing.discard();
            }
        }

        List<Entity> sourceEntities = sourceLevel.getEntities((Entity) null, bounds, ChunkSync::isColumnEntity);
        for (Entity sourceEntity : sourceEntities) {
            if (!ChunkPos.containing(sourceEntity.blockPosition()).equals(pos)) {
                continue;
            }

            try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(Constants.LOG)) {
                TagValueOutput output = TagValueOutput.createWithContext(reporter, sourceLevel.registryAccess());
                if (!sourceEntity.save(output)) {
                    continue;
                }
                Entity clone = EntityType.loadEntityRecursive(
                    output.buildResult(), buildingLevel, new EntitySpawnRequest(EntitySpawnReason.LOAD, true), entity -> entity);
                if (clone != null) {
                    buildingLevel.addFreshEntity(clone);
                }
            }
        }
    }

    private static boolean isColumnEntity(Entity entity) {
        return !(entity instanceof ServerPlayer);
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

    private static void completeOne(MinecraftServer server, UUID requester) {
        int[] counts = PROGRESS.get(requester);
        if (counts == null) {
            return;
        }

        counts[1]++;
        if (counts[1] < counts[0]) {
            return;
        }

        PROGRESS.remove(requester);
        ServerPlayer player = server.getPlayerList().getPlayer(requester);
        if (player != null) {
            player.sendSystemMessage(Component.translatable("commands.building_dimension.sync.complete", counts[0]));
        }
    }

    /**
     * Drops every queued/in-flight job and progress record. Called on server stop: jobs hold
     * {@link ResourceKey}s and, once ready, direct {@link ServerLevel}/{@link LevelChunk}
     * references that belong to the server instance being torn down — carrying them into a
     * differently-loaded world (a singleplayer "leave world" followed by opening another) would
     * copy chunks into the wrong world or leak the old world's chunks in memory.
     */
    public static void reset() {
        PENDING.clear();
        READY.clear();
        PROGRESS.clear();
        inFlight = 0;
    }
}
