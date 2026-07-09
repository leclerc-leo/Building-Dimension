package net.buildingdimension.test;

import net.buildingdimension.dimension.BuildingDimensions;
import net.buildingdimension.mixin.MinecraftServerAccessor;
import net.buildingdimension.mixin.ServerLevelAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * GameTest bodies for {@code /switch}, shared between the Fabric and NeoForge test registrations
 * (each loader just wraps these in whatever its own gametest framework expects). Every test drives
 * the command through the real dispatcher, the same way a player triggers it, and asserts on the
 * resulting player/dimension state rather than any internal return value.
 * <p>
 * Tests that switch the same player twice have to wait out {@code SwitchCommand}'s real 5 second
 * cooldown between calls. The gametest server ticks as fast as it can rather than pacing itself to
 * real time (a full 8-test suite here completes in ~1 real second), so {@link GameTestHelper}'s
 * tick-based delays do not correspond to elapsed wall-clock time; a genuine {@link Thread#sleep}
 * is the only way to actually wait out the cooldown.
 */
public final class SwitchGameTests {

    private static final long COOLDOWN_MILLIS = 5200L;

    private SwitchGameTests() {
    }

    public static void testEnterBuildingDimensionWipesStateAndEntersCreative(GameTestHelper helper) {
        ServerPlayer player = spawnSurvivalPlayer(helper);
        helper.assertValueEqual(player.level().dimension(), Level.OVERWORLD, "mock player's starting dimension");

        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
        player.experienceLevel = 10;
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 200));
        Vec3 sourcePos = player.position();

        runSwitch(player);

        ResourceKey<Level> expected = BuildingDimensions.buildingKeyOf(Level.OVERWORLD);
        helper.assertValueEqual(player.level().dimension(), expected, "player dimension after entering the building dimension");
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "expected creative mode in the building dimension");
        helper.assertTrue(player.position().distanceTo(sourcePos) < 0.01, "expected the same coordinates across the switch");
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "expected the inventory wiped on a first visit");
        helper.assertTrue(player.experienceLevel == 0, "expected xp reset on a first visit");
        helper.assertTrue(player.getActiveEffects().isEmpty(), "expected effects cleared on a first visit");
        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "expected full health on a first visit");

        helper.succeed();
    }

    public static void testReturnFromBuildingDimensionRestoresSourceState(GameTestHelper helper) {
        ServerPlayer player = spawnSurvivalPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(Items.GOLDEN_APPLE, 3));
        Vec3 sourcePos = player.position();
        float sourceYRot = player.getYRot();

        runSwitch(player);
        helper.assertValueEqual(player.level().dimension(), BuildingDimensions.buildingKeyOf(Level.OVERWORLD), "dimension right after entering");

        sleepPastCooldown();
        runSwitch(player);

        helper.assertValueEqual(player.level().dimension(), Level.OVERWORLD, "player dimension after returning");
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "expected the original survival mode restored");
        helper.assertTrue(player.position().distanceTo(sourcePos) < 0.01, "expected the original coordinates restored");
        helper.assertTrue(player.getYRot() == sourceYRot, "expected the original rotation restored");
        ItemStack restored = player.getInventory().getItem(0);
        helper.assertTrue(restored.getItem() == Items.GOLDEN_APPLE && restored.getCount() == 3, "expected the original inventory restored");

        helper.succeed();
    }

    public static void testBuildingInventoryPersistsAcrossVisits(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerPlayer player = spawnSurvivalPlayer(helper);
        ResourceKey<Level> buildingKey = BuildingDimensions.buildingKeyOf(Level.OVERWORLD);

        runSwitch(player);
        helper.assertValueEqual(player.level().dimension(), buildingKey, "dimension right after the first entry");
        ServerLevel firstBuildingLevel = server.getLevel(buildingKey);
        helper.assertTrue(firstBuildingLevel != null, "expected the building dimension to be created");
        player.getInventory().setItem(0, new ItemStack(Items.EMERALD_BLOCK, 4));

        sleepPastCooldown();
        runSwitch(player);
        helper.assertValueEqual(player.level().dimension(), Level.OVERWORLD, "expected to be back in the overworld");

        sleepPastCooldown();
        runSwitch(player);

        helper.assertValueEqual(player.level().dimension(), buildingKey, "expected the same building dimension on the second visit");
        ServerLevel secondBuildingLevel = server.getLevel(buildingKey);
        helper.assertTrue(secondBuildingLevel == firstBuildingLevel, "expected the building dimension to be reused, not recreated");
        ItemStack stashed = player.getInventory().getItem(0);
        helper.assertTrue(stashed.getItem() == Items.EMERALD_BLOCK && stashed.getCount() == 4, "expected the creative inventory to persist across visits");

        helper.succeed();
    }

    public static void testSwitchFromNetherUsesNetherCounterpart(GameTestHelper helper) {
        assertDimensionGetsCounterpart(helper, Level.NETHER);
        helper.succeed();
    }

    public static void testSwitchFromEndUsesEndCounterpart(GameTestHelper helper) {
        assertDimensionGetsCounterpart(helper, Level.END);
        helper.succeed();
    }

    public static void testSwitchFromCustomDimensionUsesNamespacedCounterpart(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ResourceKey<Level> customKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("buildingdimensiontest", "custom_land"));
        ServerLevel customLevel = createModdedStyleDimension(server, customKey);

        ServerPlayer player = spawnSurvivalPlayer(helper);
        player.teleportTo(customLevel, 0.5, 64.0, 0.5, Set.of(), 0f, 0f, false);

        runSwitch(player);

        ResourceKey<Level> expected = BuildingDimensions.buildingKeyOf(customKey);
        helper.assertValueEqual(player.level().dimension(), expected, "expected a namespaced counterpart for a non-vanilla dimension");
        helper.assertTrue(
            expected.identifier().getPath().equals("buildingdimensiontest_custom_land"),
            "expected the counterpart path to combine the source namespace and path"
        );
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "expected creative mode");
        helper.assertTrue(server.getLevel(expected) != null, "expected the custom dimension's building counterpart to be created");

        helper.succeed();
    }

    public static void testCooldownRejectsImmediateSecondSwitch(GameTestHelper helper) {
        ServerPlayer player = spawnSurvivalPlayer(helper);

        runSwitch(player);
        ResourceKey<Level> afterFirstSwitch = player.level().dimension();
        helper.assertValueEqual(afterFirstSwitch, BuildingDimensions.buildingKeyOf(Level.OVERWORLD), "expected the first switch to enter the building dimension");

        runSwitch(player);
        helper.assertValueEqual(player.level().dimension(), afterFirstSwitch, "expected an immediate second /switch to be rejected by the cooldown");

        helper.succeed();
    }

    private static void assertDimensionGetsCounterpart(GameTestHelper helper, ResourceKey<Level> sourceKey) {
        MinecraftServer server = server(helper);
        ServerLevel source = server.getLevel(sourceKey);
        helper.assertTrue(source != null, "expected " + sourceKey.identifier() + " to be loaded");

        ServerPlayer player = spawnSurvivalPlayer(helper);
        player.teleportTo(source, 0.5, 64.0, 0.5, Set.of(), 0f, 0f, false);

        runSwitch(player);

        ResourceKey<Level> expected = BuildingDimensions.buildingKeyOf(sourceKey);
        helper.assertValueEqual(player.level().dimension(), expected, "player dimension after switching from " + sourceKey.identifier());
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "expected creative mode");
        helper.assertTrue(server.getLevel(expected) != null, "expected the building counterpart to be created");
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

    private static void sleepPastCooldown() {
        try {
            Thread.sleep(COOLDOWN_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
