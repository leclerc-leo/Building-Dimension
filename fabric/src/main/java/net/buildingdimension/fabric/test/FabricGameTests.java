package net.buildingdimension.fabric.test;

import net.buildingdimension.test.AccessGameTests;
import net.buildingdimension.test.AccessoryGameTests;
import net.buildingdimension.test.DeathGameTests;
import net.buildingdimension.test.DisconnectGameTests;
import net.buildingdimension.test.GameRulesGameTests;
import net.buildingdimension.test.PortalGameTests;
import net.buildingdimension.test.SwitchGameTests;
import net.buildingdimension.test.SyncGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Wires the shared {@link SwitchGameTests} and {@link SyncGameTests} bodies into Fabric's
 * gametest entrypoint. Only runs with {@code -Dfabric-api.gametest=true} (Loom's
 * {@code runGameTest} task) or in a dev environment.
 */
public class FabricGameTests {

    private static final String STRUCTURE = "building_dimension:gametest/empty";

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void enterBuildingDimensionWipesStateAndEntersCreative(GameTestHelper helper) {
        SwitchGameTests.testEnterBuildingDimensionWipesStateAndEntersCreative(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 140)
    public void returnFromBuildingDimensionRestoresSourceState(GameTestHelper helper) {
        SwitchGameTests.testReturnFromBuildingDimensionRestoresSourceState(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 260)
    public void buildingInventoryPersistsAcrossVisits(GameTestHelper helper) {
        SwitchGameTests.testBuildingInventoryPersistsAcrossVisits(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void switchFromNetherUsesNetherCounterpart(GameTestHelper helper) {
        SwitchGameTests.testSwitchFromNetherUsesNetherCounterpart(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void switchFromEndUsesEndCounterpart(GameTestHelper helper) {
        SwitchGameTests.testSwitchFromEndUsesEndCounterpart(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void switchFromCustomDimensionUsesNamespacedCounterpart(GameTestHelper helper) {
        SwitchGameTests.testSwitchFromCustomDimensionUsesNamespacedCounterpart(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void cooldownRejectsImmediateSecondSwitch(GameTestHelper helper) {
        SwitchGameTests.testCooldownRejectsImmediateSecondSwitch(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 200)
    public void syncCopiesBlocksAndLightIntoBuildingDimension(GameTestHelper helper) {
        SyncGameTests.testSyncCopiesBlocksAndLightIntoBuildingDimension(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 200)
    public void syncRespectsRadius(GameTestHelper helper) {
        SyncGameTests.testSyncRespectsRadius(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 200)
    public void syncFromInsideBuildingDimensionCopiesFromSource(GameTestHelper helper) {
        SyncGameTests.testSyncFromInsideBuildingDimensionCopiesFromSource(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void portalBlockedLeavingBuildingDimension(GameTestHelper helper) {
        PortalGameTests.testPortalBlockedLeavingBuildingDimension(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void portalBlockedEnteringBuildingDimension(GameTestHelper helper) {
        PortalGameTests.testPortalBlockedEnteringBuildingDimension(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void portalUnaffectedBetweenRealDimensions(GameTestHelper helper) {
        PortalGameTests.testPortalUnaffectedBetweenRealDimensions(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void deathInBuildingDimensionRespawnsInBuildingDimension(GameTestHelper helper) {
        DeathGameTests.testDeathInBuildingDimensionRespawnsInBuildingDimension(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void deathInRealDimensionUnaffected(GameTestHelper helper) {
        DeathGameTests.testDeathInRealDimensionUnaffected(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void buildingDimensionRestoredBeforeReconnect(GameTestHelper helper) {
        DisconnectGameTests.testBuildingDimensionRestoredBeforeReconnect(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void accessoryHookIsNoOpWithoutAnAccessoryModInstalled(GameTestHelper helper) {
        AccessoryGameTests.testAccessoryHookIsNoOpWithoutAnAccessoryModInstalled(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void switchSucceedsWithoutAnAccessoryModInstalled(GameTestHelper helper) {
        AccessoryGameTests.testSwitchSucceedsWithoutAnAccessoryModInstalled(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void accessorySlotRoundTripsWhenAccessoryModInstalled(GameTestHelper helper) {
        AccessoryGameTests.testAccessorySlotRoundTripsWhenAccessoryModInstalled(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void mobGriefingDisabledByDefault(GameTestHelper helper) {
        GameRulesGameTests.testMobGriefingDisabledByDefault(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void mobSpawningDisabledByDefault(GameTestHelper helper) {
        GameRulesGameTests.testMobSpawningDisabledByDefault(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void defaultAccessIsUnrestricted(GameTestHelper helper) {
        AccessGameTests.testDefaultAccessIsUnrestricted(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void opRequiredDeniesNonOperator(GameTestHelper helper) {
        AccessGameTests.testOpRequiredDeniesNonOperator(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void opRequiredWithSpectatorFallbackAllowsSpectator(GameTestHelper helper) {
        AccessGameTests.testOpRequiredWithSpectatorFallbackAllowsSpectator(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 40)
    public void whitelistGatesAccess(GameTestHelper helper) {
        AccessGameTests.testWhitelistGatesAccess(helper);
    }

    @GameTest(structure = STRUCTURE, maxTicks = 200)
    public void syncCopiesChestContentsAndItemFrame(GameTestHelper helper) {
        SyncGameTests.testSyncCopiesChestContentsAndItemFrame(helper);
    }
}
