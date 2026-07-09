package net.buildingdimension.test;

import net.buildingdimension.dimension.DimensionFactory;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/**
 * GameTest bodies for the portal-escape mixin ({@code EntityMixin}), shared between the Fabric and
 * NeoForge test registrations. These call {@link net.minecraft.world.entity.Entity#canTeleport}
 * directly rather than driving an actual nether/end portal end to end, since that method is the
 * single choke point every portal implementation defers to before committing a cross-dimension
 * teleport — exercising it directly is enough to prove the mixin's behavior without needing a lit
 * portal and a multi-tick teleport sequence.
 */
public final class PortalGameTests {

    private PortalGameTests() {
    }

    public static void testPortalBlockedLeavingBuildingDimension(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel overworld = server.overworld();
        ServerLevel building = server.getLevel(DimensionFactory.getOrCreate(server, overworld));
        ServerPlayer player = spawnPlayer(helper);

        helper.assertTrue(!player.canTeleport(building, overworld), "expected a portal to be blocked leaving a building dimension");

        helper.succeed();
    }

    public static void testPortalBlockedEnteringBuildingDimension(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel overworld = server.overworld();
        ServerLevel building = server.getLevel(DimensionFactory.getOrCreate(server, overworld));
        ServerPlayer player = spawnPlayer(helper);

        helper.assertTrue(!player.canTeleport(overworld, building), "expected a portal to be blocked entering a building dimension");

        helper.succeed();
    }

    public static void testPortalUnaffectedBetweenRealDimensions(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel overworld = server.overworld();
        ServerLevel nether = server.getLevel(Level.NETHER);
        ServerPlayer player = spawnPlayer(helper);

        helper.assertTrue(player.canTeleport(overworld, nether), "expected ordinary portal travel between real dimensions to be unaffected");

        helper.succeed();
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    @SuppressWarnings({"removal"})
    private static ServerPlayer spawnPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }
}
