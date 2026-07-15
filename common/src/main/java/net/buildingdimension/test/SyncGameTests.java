package net.buildingdimension.test;

import net.buildingdimension.dimension.BuildingDimensions;
import net.buildingdimension.mixin.MinecraftServerAccessor;
import net.buildingdimension.mixin.ServerLevelAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;

import java.util.List;
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

    /**
     * Confirms the two riskiest pieces of {@code ChunkSync}'s copy logic: a block entity's actual
     * contents (a chest's items, not just the chest block itself) and a decorative entity (an item
     * frame, plus the item it's holding) both survive the source -> building copy. Uses its own
     * custom dimension (the same way {@link SwitchGameTests#testSwitchFromCustomDimensionUsesNamespacedCounterpart}
     * does) since overworld/nether/end are already claimed by the other tests in this class.
     */
    public static void testSyncCopiesChestContentsAndItemFrame(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ResourceKey<Level> customKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("buildingdimensiontest", "sync_entities_land"));
        ServerLevel customLevel = createModdedStyleDimension(server, customKey);

        ServerPlayer player = spawnSurvivalPlayer(helper);
        player.teleportTo(customLevel, 0.5, 64.0, 0.5, Set.of(), 0f, 0f, false);

        BlockPos chestPos = player.blockPosition().offset(2, 0, 0);
        customLevel.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        if (customLevel.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
        }

        ItemFrame itemFrame = new ItemFrame(customLevel, player.blockPosition().offset(3, 0, 0), Direction.NORTH);
        itemFrame.setItem(new ItemStack(Items.EMERALD));
        customLevel.addFreshEntity(itemFrame);

        runSync(player, 1);

        ResourceKey<Level> buildingKey = BuildingDimensions.buildingKeyOf(customKey);
        helper.succeedWhen(() -> {
            ServerLevel buildingLevel = server.getLevel(buildingKey);
            helper.assertTrue(buildingLevel != null, "expected /sync to create the building dimension");
            helper.assertTrue(buildingLevel.getBlockState(chestPos).is(Blocks.CHEST), "expected the chest block to be copied");
            helper.assertTrue(
                buildingLevel.getBlockEntity(chestPos) instanceof ChestBlockEntity copiedChest
                    && copiedChest.getItem(0).is(Items.DIAMOND) && copiedChest.getItem(0).getCount() == 5,
                "expected the chest's contents to be copied via its block entity"
            );

            AABB bounds = new AABB(chestPos).inflate(4);
            List<ItemFrame> frames = buildingLevel.getEntities(EntityTypes.ITEM_FRAME, bounds, frame -> frame.getItem().is(Items.EMERALD));
            helper.assertTrue(!frames.isEmpty(), "expected the item frame and its held item to be copied");
        });
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    private static ServerLevel createModdedStyleDimension(MinecraftServer server, ResourceKey<Level> key) {
        ServerLevel overworld = server.overworld();
        MinecraftServerAccessor serverAccessor = (MinecraftServerAccessor) server;
        boolean tickTime = ((ServerLevelAccessor) overworld).buildingDimension$getTickTime();

        LevelStem levelStem = new LevelStem(overworld.dimensionTypeRegistration(), overworld.getChunkSource().getGenerator());
        ServerLevelData levelData = new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData());
        long biomeZoomSeed = BiomeManager.obfuscateSeed(overworld.getSeed());

        ServerLevel level = new ServerLevel(
            server,
            Util.backgroundExecutor(),
            serverAccessor.buildingDimension$getStorageSource(),
            levelData,
            key,
            levelStem,
            server.getWorldData().isDebugWorld(),
            biomeZoomSeed,
            List.of(),
            tickTime
        );

        serverAccessor.buildingDimension$getLevels().put(key, level);
        return level;
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
