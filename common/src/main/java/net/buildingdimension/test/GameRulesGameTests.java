package net.buildingdimension.test;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * GameTest bodies for {@code BuildingDimensionCommon#applyConfiguredGameRules}. By the time any
 * GameTest body runs, the gametest server has already gone through its normal boot sequence (mod
 * init, then {@code ServerStartedEvent}/{@code SERVER_STARTED}), so these just assert on the
 * server's actual {@link GameRules} state rather than calling anything directly — there's no
 * separate choke point to call, the "choke point" is server startup itself.
 */
public final class GameRulesGameTests {

    private GameRulesGameTests() {
    }

    public static void testMobGriefingDisabledByDefault(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();

        helper.assertValueEqual(server.getGameRules().get(GameRules.MOB_GRIEFING), Boolean.FALSE,
            "expected mobGriefing to be forced off on server start (disableMobGriefing defaults to true)");

        helper.succeed();
    }

    public static void testMobSpawningDisabledByDefault(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();

        helper.assertValueEqual(server.getGameRules().get(GameRules.SPAWN_MOBS), Boolean.FALSE,
            "expected doMobSpawning to be forced off on server start (disableMobSpawning defaults to true)");

        helper.succeed();
    }
}
