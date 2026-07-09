package net.buildingdimension.test;

import net.buildingdimension.dimension.BuildingDimensions;
import net.buildingdimension.mixin.MinecraftServerAccessor;
import net.buildingdimension.dimension.DimensionFactory;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/**
 * GameTest bodies verifying that a player who logs out while inside a building dimension resumes
 * there correctly — even across a server restart, which is the only case that actually needs
 * {@code DimensionFactory#restoreAll} (an ordinary disconnect/reconnect on a still-running server
 * never unloads the in-memory {@link ServerLevel} at all). GameTest can't drive a real client
 * disconnect/reconnect, so this simulates the restart case the same way {@link PortalGameTests} and
 * {@link DeathGameTests} exercise their mixins directly: drop the building dimension's in-memory
 * {@code ServerLevel} the way it would be gone right after a boot, then call
 * {@code DimensionFactory#restoreAll} the same way {@code BuildingDimensionCommon#onServerStarted}
 * does, and confirm the dimension a reconnecting player's saved position points at exists again
 * before any connection is accepted.
 */
public final class DisconnectGameTests {

    private DisconnectGameTests() {
    }

    public static void testBuildingDimensionRestoredBeforeReconnect(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel overworld = server.overworld();
        ResourceKey<Level> buildingKey = BuildingDimensions.buildingKeyOf(Level.OVERWORLD);
        ServerLevel building = server.getLevel(DimensionFactory.getOrCreate(server, overworld));

        ServerPlayer player = spawnPlayer(helper);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_BLOCK, 2));

        MinecraftServerAccessor serverAccessor = (MinecraftServerAccessor) server;
        serverAccessor.buildingDimension$getLevels().remove(buildingKey);
        helper.assertTrue(server.getLevel(buildingKey) == null, "expected the building dimension's level to be gone, simulating a fresh boot");

        DimensionFactory.restoreAll(server);

        ServerLevel restored = server.getLevel(buildingKey);
        helper.assertTrue(restored != null, "expected restoreAll to recreate the building dimension a reconnecting player's saved position points at");
        helper.assertTrue(building != null, "expected the building dimension to have existed before the simulated restart");

        helper.succeed();
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    @SuppressWarnings({"removal"})
    private static ServerPlayer spawnPlayer(GameTestHelper helper) {
        return helper.makeMockServerPlayerInLevel();
    }
}
