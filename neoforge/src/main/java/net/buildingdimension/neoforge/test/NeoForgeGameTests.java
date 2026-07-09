package net.buildingdimension.neoforge.test;

import net.buildingdimension.Constants;
import net.buildingdimension.test.AccessoryGameTests;
import net.buildingdimension.test.DeathGameTests;
import net.buildingdimension.test.DisconnectGameTests;
import net.buildingdimension.test.PortalGameTests;
import net.buildingdimension.test.SwitchGameTests;
import net.buildingdimension.test.SyncGameTests;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Consumer;

/**
 * Wires the shared {@link SwitchGameTests} and {@link SyncGameTests} bodies into NeoForge's
 * data-driven gametest registries: one entry per test in {@code Registries.TEST_FUNCTION} (the
 * actual test body), plus a matching {@code Registries.TEST_INSTANCE} entry (what the gametest
 * server schedules). {@code TEST_FUNCTION} is a vanilla "simple" registry that freezes very
 * early, so the functions have to go through a {@link DeferredRegister} (hooked to FML's
 * registration lifecycle) rather than a direct {@code Registry.register} call in the mod
 * constructor, which is already too late.
 * <p>
 * Only runs with {@code -Dneoforge.enableGameTest=true} (the {@code gameTestServer} run) or in an IDE.
 */
public final class NeoForgeGameTests {

    private static final Identifier STRUCTURE = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "gametest/empty");

    private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, Constants.MOD_ID);

    static {
        register("enter_building_dimension_wipes_state", SwitchGameTests::testEnterBuildingDimensionWipesStateAndEntersCreative);
        register("return_from_building_dimension_restores_source_state", SwitchGameTests::testReturnFromBuildingDimensionRestoresSourceState);
        register("building_inventory_persists_across_visits", SwitchGameTests::testBuildingInventoryPersistsAcrossVisits);
        register("switch_from_nether_uses_nether_counterpart", SwitchGameTests::testSwitchFromNetherUsesNetherCounterpart);
        register("switch_from_end_uses_end_counterpart", SwitchGameTests::testSwitchFromEndUsesEndCounterpart);
        register("switch_from_custom_dimension_uses_namespaced_counterpart", SwitchGameTests::testSwitchFromCustomDimensionUsesNamespacedCounterpart);
        register("cooldown_rejects_immediate_second_switch", SwitchGameTests::testCooldownRejectsImmediateSecondSwitch);
        register("sync_copies_blocks_and_light_into_building_dimension", SyncGameTests::testSyncCopiesBlocksAndLightIntoBuildingDimension);
        register("sync_respects_radius", SyncGameTests::testSyncRespectsRadius);
        register("sync_from_inside_building_dimension_copies_from_source", SyncGameTests::testSyncFromInsideBuildingDimensionCopiesFromSource);
        register("portal_blocked_leaving_building_dimension", PortalGameTests::testPortalBlockedLeavingBuildingDimension);
        register("portal_blocked_entering_building_dimension", PortalGameTests::testPortalBlockedEnteringBuildingDimension);
        register("portal_unaffected_between_real_dimensions", PortalGameTests::testPortalUnaffectedBetweenRealDimensions);
        register("death_in_building_dimension_respawns_in_building_dimension", DeathGameTests::testDeathInBuildingDimensionRespawnsInBuildingDimension);
        register("death_in_real_dimension_unaffected", DeathGameTests::testDeathInRealDimensionUnaffected);
        register("building_dimension_restored_before_reconnect", DisconnectGameTests::testBuildingDimensionRestoredBeforeReconnect);
        register("accessory_hook_is_no_op_without_an_accessory_mod_installed", AccessoryGameTests::testAccessoryHookIsNoOpWithoutAnAccessoryModInstalled);
        register("switch_succeeds_without_an_accessory_mod_installed", AccessoryGameTests::testSwitchSucceedsWithoutAnAccessoryModInstalled);
        register("accessory_slot_round_trips_when_accessory_mod_installed", AccessoryGameTests::testAccessorySlotRoundTripsWhenAccessoryModInstalled);
    }

    private NeoForgeGameTests() {
    }

    public static void register(IEventBus modEventBus) {
        TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(NeoForgeGameTests::onRegisterGameTests);
    }

    private static void register(String name, Consumer<GameTestHelper> function) {
        TEST_FUNCTIONS.register(name, () -> function);
    }

    private static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "test_environment"));

        registerInstance(event, environment, "enter_building_dimension_wipes_state", 40);
        registerInstance(event, environment, "return_from_building_dimension_restores_source_state", 140);
        registerInstance(event, environment, "building_inventory_persists_across_visits", 260);
        registerInstance(event, environment, "switch_from_nether_uses_nether_counterpart", 40);
        registerInstance(event, environment, "switch_from_end_uses_end_counterpart", 40);
        registerInstance(event, environment, "switch_from_custom_dimension_uses_namespaced_counterpart", 40);
        registerInstance(event, environment, "cooldown_rejects_immediate_second_switch", 40);
        registerInstance(event, environment, "sync_copies_blocks_and_light_into_building_dimension", 200);
        registerInstance(event, environment, "sync_respects_radius", 200);
        registerInstance(event, environment, "sync_from_inside_building_dimension_copies_from_source", 200);
        registerInstance(event, environment, "portal_blocked_leaving_building_dimension", 40);
        registerInstance(event, environment, "portal_blocked_entering_building_dimension", 40);
        registerInstance(event, environment, "portal_unaffected_between_real_dimensions", 40);
        registerInstance(event, environment, "death_in_building_dimension_respawns_in_building_dimension", 40);
        registerInstance(event, environment, "death_in_real_dimension_unaffected", 40);
        registerInstance(event, environment, "building_dimension_restored_before_reconnect", 40);
        registerInstance(event, environment, "accessory_hook_is_no_op_without_an_accessory_mod_installed", 40);
        registerInstance(event, environment, "switch_succeeds_without_an_accessory_mod_installed", 40);
        registerInstance(event, environment, "accessory_slot_round_trips_when_accessory_mod_installed", 40);
    }

    private static void registerInstance(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, String name, int maxTicks) {
        Identifier id = Identifier.fromNamespaceAndPath(Constants.MOD_ID, name);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, STRUCTURE, maxTicks, 0, true);
        event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id), data));
    }
}
