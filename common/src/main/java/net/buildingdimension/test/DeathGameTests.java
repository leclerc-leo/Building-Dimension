package net.buildingdimension.test;

import net.buildingdimension.dimension.BuildingDimensions;
import net.buildingdimension.dimension.DimensionFactory;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.Set;

/**
 * GameTest bodies for the death-respawn mixin ({@code ServerPlayerMixin}), shared between the
 * Fabric and NeoForge test registrations. These call {@code PlayerList#respawn} directly rather
 * than driving an actual death (damage source, health to zero, death event), the same way
 * {@link PortalGameTests} calls {@code canTeleport} directly — {@code respawn} is the single choke
 * point vanilla funnels every respawn through regardless of cause of death, so exercising it
 * directly is enough to prove the mixin's behavior.
 */
public final class DeathGameTests {

    private DeathGameTests() {
    }

    public static void testDeathInBuildingDimensionRespawnsInBuildingDimension(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerLevel overworld = server.overworld();
        ResourceKey<Level> buildingKey = BuildingDimensions.buildingKeyOf(Level.OVERWORLD);
        ServerLevel building = server.getLevel(DimensionFactory.getOrCreate(server, overworld));

        ServerPlayer player = spawnPlayer(helper);
        player.teleportTo(building, 0.5, 64.0, 0.5, Set.of(), 0f, 0f, false);
        player.setGameMode(GameType.CREATIVE);
        helper.assertValueEqual(player.level().dimension(), buildingKey, "expected the player in the building dimension before dying");

        ServerPlayer respawned = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);

        helper.assertValueEqual(respawned.level().dimension(), buildingKey, "expected death in a building dimension to respawn back into it, not the real overworld");

        helper.succeed();
    }

    public static void testDeathInRealDimensionUnaffected(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        ServerPlayer player = spawnPlayer(helper);
        helper.assertValueEqual(player.level().dimension(), Level.OVERWORLD, "expected the player in the real overworld before dying");

        ServerPlayer respawned = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);

        helper.assertTrue(!BuildingDimensions.isBuildingDimension(respawned.level().dimension()), "expected an ordinary death outside a building dimension to be unaffected by the mixin");

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
