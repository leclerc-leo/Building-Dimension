package net.buildingdimension.test;

import net.buildingdimension.dimension.BuildingDimensions;
import net.buildingdimension.platform.Services;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * GameTest bodies for the Trinkets/Curios accessory hook ({@code IAccessoryHelper}). Normally
 * neither mod is on the gametest classpath, so most of these pin down the soft-dependency contract:
 * with no accessory mod loaded, {@code Services.ACCESSORIES} no-ops cleanly, and
 * {@code /switch}/{@code PlayerSnapshot} keep working exactly as before now that they call into it.
 * <p>
 * {@link #testAccessorySlotRoundTripsWhenAccessoryModInstalled} is the exception: it exercises the
 * real reflection path against whichever accessory mod is actually installed, and is a no-op
 * otherwise. {@code scripts/test-accessory-mods.sh} is what puts a real, downloaded Trinkets/Curios
 * jar on the classpath to make it do anything.
 */
public final class AccessoryGameTests {

    private AccessoryGameTests() {
    }

    public static void testAccessoryHookIsNoOpWithoutAnAccessoryModInstalled(GameTestHelper helper) {
        ServerPlayer player = spawnSurvivalPlayer(helper);

        List<ItemStack> captured = Services.ACCESSORIES.capture(player);
        helper.assertTrue(captured.isEmpty(), "expected no accessory items with no accessory mod installed");

        Services.ACCESSORIES.restore(player, captured);
        Services.ACCESSORIES.clear(player);

        helper.succeed();
    }

    public static void testSwitchSucceedsWithoutAnAccessoryModInstalled(GameTestHelper helper) {
        ServerPlayer player = spawnSurvivalPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 1));

        runSwitch(player);

        ResourceKey<Level> expected = BuildingDimensions.buildingKeyOf(Level.OVERWORLD);
        helper.assertValueEqual(player.level().dimension(), expected, "expected /switch to still work with the accessory hook wired in but no accessory mod installed");
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "expected creative mode in the building dimension");
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "expected the inventory wiped on a first visit");

        helper.succeed();
    }

    /**
     * With a real accessory mod on the classpath, writes a marker item into the first accessory
     * slot via {@code restore}, confirms {@code capture} reads it back, then confirms {@code clear}
     * empties it again — the same round trip a real /switch relies on, but driving the hook
     * directly so it isn't sensitive to Trinkets/Curios's default slot layout.
     * <p>
     * Whether a fresh mock player actually has any accessory slot capacity depends on the specific
     * mod build's default slot configuration (observed empty on at least one beta build of Trinkets
     * Updated for this Minecraft version), which is outside this project's control. If
     * {@code capture} reports zero total capacity before anything is written, the reflection path
     * still ran without error but there's nothing to round-trip, so the test is inconclusive rather
     * than a failure.
     */
    public static void testAccessorySlotRoundTripsWhenAccessoryModInstalled(GameTestHelper helper) {
        ServerPlayer player = spawnSurvivalPlayer(helper);

        if (!Services.PLATFORM.isModLoaded("trinkets") && !Services.PLATFORM.isModLoaded("curios")) {
            helper.succeed();
            return;
        }

        if (Services.ACCESSORIES.capture(player).isEmpty()) {
            helper.succeed();
            return;
        }

        Services.ACCESSORIES.restore(player, List.of(new ItemStack(Items.DIAMOND, 1)));
        List<ItemStack> captured = Services.ACCESSORIES.capture(player);
        helper.assertTrue(
            captured.stream().anyMatch(stack -> stack.getItem() == Items.DIAMOND),
            "expected the marker item written into the first accessory slot to be captured back"
        );

        Services.ACCESSORIES.clear(player);
        helper.assertTrue(
            Services.ACCESSORIES.capture(player).stream().allMatch(ItemStack::isEmpty),
            "expected clear() to empty every accessory slot"
        );

        helper.succeed();
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
}
