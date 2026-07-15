package net.buildingdimension.test;

import net.buildingdimension.command.SwitchAccess;
import net.buildingdimension.config.BuildingDimensionConfig;
import net.buildingdimension.config.SwitchWhitelist;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * GameTest bodies for {@code SwitchAccess} (the operator/whitelist/spectator-fallback gate in
 * front of {@code /switch}). Calls {@link SwitchAccess#check} directly rather than running the
 * full {@code /switch} command, since these are only exercising the access decision itself, not
 * the teleport/snapshot machinery {@link SwitchGameTests} already covers.
 * <p>
 * {@link BuildingDimensionConfig}'s access settings are static/server-wide, so every test here
 * flips them and reverts them back to the defaults before returning — safe because a GameTest body
 * runs synchronously to completion on the single main thread, so no other concurrently-scheduled
 * test's tick can interleave mid-method.
 */
public final class AccessGameTests {

    private AccessGameTests() {
    }

    public static void testDefaultAccessIsUnrestricted(GameTestHelper helper) {
        ServerPlayer player = spawnPlayer(helper);
        CommandSourceStack source = player.createCommandSourceStack();

        helper.assertTrue(SwitchAccess.check(player, source) == SwitchAccess.Result.ALLOWED,
            "expected /switch to be unrestricted with the default config");

        helper.succeed();
    }

    public static void testOpRequiredDeniesNonOperator(GameTestHelper helper) {
        ServerPlayer player = spawnPlayer(helper);
        CommandSourceStack source = player.createCommandSourceStack();

        BuildingDimensionConfig.setSwitchAccessForTest(true, false, false);
        try {
            helper.assertTrue(SwitchAccess.check(player, source) == SwitchAccess.Result.DENIED,
                "expected a non-operator mock player to be denied when switchRequireOp is enabled");
        } finally {
            BuildingDimensionConfig.setSwitchAccessForTest(false, false, false);
        }

        helper.succeed();
    }

    public static void testOpRequiredWithSpectatorFallbackAllowsSpectator(GameTestHelper helper) {
        ServerPlayer player = spawnPlayer(helper);
        CommandSourceStack source = player.createCommandSourceStack();

        BuildingDimensionConfig.setSwitchAccessForTest(true, false, true);
        try {
            helper.assertTrue(SwitchAccess.check(player, source) == SwitchAccess.Result.DENIED_SPECTATOR,
                "expected a denied player to fall back to spectator when switchDeniedPlayersUseSpectator is enabled");
        } finally {
            BuildingDimensionConfig.setSwitchAccessForTest(false, false, false);
        }

        helper.succeed();
    }

    public static void testWhitelistGatesAccess(GameTestHelper helper) {
        ServerPlayer player = spawnPlayer(helper);
        CommandSourceStack source = player.createCommandSourceStack();
        String name = player.getGameProfile().name();

        BuildingDimensionConfig.setSwitchAccessForTest(false, true, false);
        try {
            helper.assertTrue(!SwitchWhitelist.contains(name), "expected the mock player to not already be whitelisted");
            helper.assertTrue(SwitchAccess.check(player, source) == SwitchAccess.Result.DENIED,
                "expected an unlisted player to be denied when switchRequireWhitelist is enabled");

            SwitchWhitelist.add(name);
            helper.assertTrue(SwitchAccess.check(player, source) == SwitchAccess.Result.ALLOWED,
                "expected a whitelisted player to be allowed");
        } finally {
            SwitchWhitelist.remove(name);
            BuildingDimensionConfig.setSwitchAccessForTest(false, false, false);
        }

        helper.succeed();
    }

    @SuppressWarnings({"removal"})
    private static ServerPlayer spawnPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }
}
