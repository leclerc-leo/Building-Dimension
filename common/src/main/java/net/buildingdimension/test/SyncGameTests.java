package net.buildingdimension.test;

import net.buildingdimension.dimension.BuildingDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/**
 * GameTest bodies for {@code /sync}, shared between the Fabric and NeoForge test registrations
 * (mirrors {@link SwitchGameTests}). Unlike {@code /switch}, syncing a chunk is spread across
 * several ticks by {@code ChunkSync}, so every test here uses {@link GameTestHelper#succeedWhen}
 * to poll for the expected end state instead of asserting immediately after the command runs.
 * <p>
 * Every mock player created by {@link GameTestHelper#makeMockServerPlayerInLevel()} lands at the
 * same absolute world position (0, 0, 0) regardless of which test spawned it or where its own
 * gametest structure was placed — there's no per-test isolation for that. Since these tests
 * actually write blocks into the shared world (unlike {@link SwitchGameTests}, which only touches
 * player state), two sync tests both targeting the overworld at the same time would stomp on each
 * other's blocks. Each test below targets a different dimension (overworld / nether / end) so they
 * can run concurrently without colliding.
 */
public final class SyncGameTests {

    private SyncGameTests() {
    }

    public static void testSyncCopiesBlocksAndLightIntoBuildingDimension(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerPlayer player = spawnSurvivalPlayer(helper);
        ServerLevel overworld = server.overworld();

        BlockPos torchPos = player.blockPosition().offset(2, 0, 2);
        overworld.setBlockAndUpdate(torchPos, Blocks.TORCH.defaultBlockState());

        runSync(player, 1);

        ResourceKey<Level> buildingKey = BuildingDimensions.buildingKeyOf(Level.OVERWORLD);
        helper.succeedWhen(() -> {
            ServerLevel buildingLevel = server.getLevel(buildingKey);
            helper.assertTrue(buildingLevel != null, "expected /sync to create the building dimension");
            helper.assertTrue(buildingLevel.getBlockState(torchPos).is(Blocks.TORCH), "expected the torch to be copied into the building dimension");
            helper.assertTrue(buildingLevel.getBrightness(LightLayer.BLOCK, torchPos.above()) > 0, "expected the copied torch to actually light the building dimension");
        });
    }

    public static void testSyncRespectsRadius(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel nether = server.getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "expected the nether to be loaded");

        ServerPlayer player = spawnSurvivalPlayer(helper);
        player.teleportTo(nether, 0.5, 64.0, 0.5, Set.of(), 0f, 0f, false);

        BlockPos nearPos = player.blockPosition();
        BlockPos farPos = nearPos.offset(32, 0, 0);
        nether.setBlockAndUpdate(nearPos, Blocks.EMERALD_BLOCK.defaultBlockState());
        nether.setBlockAndUpdate(farPos, Blocks.DIAMOND_BLOCK.defaultBlockState());

        runSync(player, 0);

        ResourceKey<Level> buildingKey = BuildingDimensions.buildingKeyOf(Level.NETHER);
        helper.succeedWhen(() -> {
            ServerLevel buildingLevel = server.getLevel(buildingKey);
            helper.assertTrue(buildingLevel != null, "expected /sync to create the building dimension");
            helper.assertTrue(buildingLevel.getBlockState(nearPos).is(Blocks.EMERALD_BLOCK), "expected the player's own chunk to be synced at radius 0");
            helper.assertFalse(buildingLevel.getBlockState(farPos).is(Blocks.DIAMOND_BLOCK), "expected a chunk outside the radius to not be synced");
        });
    }

    public static void testSyncFromInsideBuildingDimensionCopiesFromSource(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel end = server.getLevel(Level.END);
        helper.assertTrue(end != null, "expected the end to be loaded");

        ServerPlayer player = spawnSurvivalPlayer(helper);
        player.teleportTo(end, 0.5, 64.0, 0.5, Set.of(), 0f, 0f, false);
        BlockPos pos = player.blockPosition();

        runSwitch(player);
        ResourceKey<Level> buildingKey = BuildingDimensions.buildingKeyOf(Level.END);
        helper.assertValueEqual(player.level().dimension(), buildingKey, "expected the player to be in the building dimension");

        end.setBlockAndUpdate(pos, Blocks.LAPIS_BLOCK.defaultBlockState());

        runSync(player, 1);

        helper.succeedWhen(() -> {
            ServerLevel buildingLevel = server.getLevel(buildingKey);
            helper.assertTrue(
                buildingLevel.getBlockState(pos).is(Blocks.LAPIS_BLOCK),
                "expected /sync run from inside the building dimension to still copy survival -> building"
            );
        });
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    @SuppressWarnings({"removal"})
    private static ServerPlayer spawnSurvivalPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static void runSwitch(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "switch");
    }

    private static void runSync(ServerPlayer player, int radius) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "sync " + radius);
    }
}
