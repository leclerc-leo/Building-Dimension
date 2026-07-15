package net.buildingdimension.fabric.platform;

import net.buildingdimension.Constants;
import net.buildingdimension.platform.services.IAccessoryHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reflects into Trinkets (no compile-time dependency, since its exact API surface can drift across
 * Minecraft versions faster than this mod tracks them — see {@link #API_CANDIDATES}, which already
 * has to cover two incompatible entry points for the classic mod versus the "Trinkets Updated" fork
 * that's the only build published for this project's Minecraft version) to snapshot/restore/clear
 * every accessory slot, the same way {@code PlayerSnapshot} treats the vanilla inventory and ender
 * chest.
 * <p>
 * Both known entry points ultimately hand back a {@code Map<String, Map<String, TrinketInventory>>}
 * (group -> slot name -> inventory), and {@code TrinketInventory} implements vanilla's
 * {@link Container} in both, so only that lookup goes through reflection; the actual slot access
 * uses the real interface. Any mismatch between what reflection expects and what's actually
 * installed is treated as "accessories unavailable" rather than a crash.
 */
public class FabricAccessoryHelper implements IAccessoryHelper {

    private static final String MOD_ID = "trinkets";
    private static boolean warned = false;

    /**
     * className, the static lookup method taking a {@link LivingEntity}, and whether that method
     * wraps its result in an {@link Optional}. Tried in order; the first one whose class actually
     * exists on the classpath wins.
     */
    private record ApiCandidate(String className, String lookupMethod, boolean optional) {
    }

    private static final List<ApiCandidate> API_CANDIDATES = List.of(
        new ApiCandidate("eu.pb4.trinkets.api.TrinketsApi", "getAttachment", false), // Trinkets Updated (eu.pb4 fork)
        new ApiCandidate("dev.emi.trinkets.api.TrinketsApi", "getTrinketComponent", true) // classic Trinkets
    );

    @Override
    public List<ItemStack> capture(ServerPlayer player) {
        List<ItemStack> items = new ArrayList<>();
        for (Container container : trinketContainers(player)) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                items.add(container.getItem(i).copy());
            }
        }
        return items;
    }

    @Override
    public void restore(ServerPlayer player, List<ItemStack> items) {
        int index = 0;
        for (Container container : trinketContainers(player)) {
            for (int i = 0; i < container.getContainerSize() && index < items.size(); i++, index++) {
                container.setItem(i, items.get(index).copy());
            }
        }
        if (index < items.size()) {
            IAccessoryHelper.depositOverflow(player, items.subList(index, items.size()));
        }
    }

    @Override
    public void clear(ServerPlayer player) {
        for (Container container : trinketContainers(player)) {
            container.clearContent();
        }
    }

    /**
     * Every equipped TrinketInventory, ordered deterministically (sorted by group then slot name)
     * so capture/restore/clear all walk the same slots in the same order.
     */
    private static List<Container> trinketContainers(ServerPlayer player) {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return List.of();
        }

        Exception lastFailure = null;
        for (ApiCandidate candidate : API_CANDIDATES) {
            try {
                Class<?> apiClass = Class.forName(candidate.className());
                Method lookup = apiClass.getMethod(candidate.lookupMethod(), LivingEntity.class);
                Object result = lookup.invoke(null, player);

                Object component;
                if (candidate.optional()) {
                    Optional<?> optional = (Optional<?>) result;
                    if (optional.isEmpty()) {
                        return List.of();
                    }
                    component = optional.get();
                } else {
                    component = result;
                }

                Method getInventory = component.getClass().getMethod("getInventory");
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Container>> inventory = (Map<String, Map<String, Container>>) getInventory.invoke(component);
                return sortedContainers(inventory);
            } catch (ClassNotFoundException e) {
                // Not this candidate's API — try the next one.
            } catch (Exception e) {
                lastFailure = e;
                break;
            }
        }

        if (!warned) {
            warned = true;
            Constants.LOG.warn("Trinkets is installed but its API didn't match what Building Dimension expects; accessory slots won't be snapshotted", lastFailure);
        }
        return List.of();
    }

    private static List<Container> sortedContainers(Map<String, Map<String, Container>> inventory) {
        List<Container> containers = new ArrayList<>();
        inventory.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(groupEntry -> groupEntry.getValue().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(slotEntry -> containers.add(slotEntry.getValue())));
        return containers;
    }
}
