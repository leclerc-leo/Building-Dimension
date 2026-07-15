package net.buildingdimension.neoforge.platform;

import net.buildingdimension.Constants;
import net.buildingdimension.platform.services.IAccessoryHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Reflects into Curios (no compile-time dependency, for the same reason as Trinkets on Fabric: its
 * API can drift faster than this mod tracks new Minecraft versions) to snapshot/restore/clear every
 * curio slot.
 * <p>
 * {@link IItemHandlerModifiable} is a NeoForge core type, not a Curios one, so only the
 * Curios-specific lookup (CuriosApi -> ICuriosItemHandler -> ICurioStacksHandler) goes through
 * reflection; the actual slot access uses the real interface. Any mismatch between what reflection
 * expects and what's actually installed is treated as "accessories unavailable" rather than a
 * crash.
 */
@SuppressWarnings("deprecation") // IItemHandlerModifiable, deprecated in favor of NeoForge's newer transfer API but still the API Curios itself exposes
public class NeoForgeAccessoryHelper implements IAccessoryHelper {

    private static final String MOD_ID = "curios";
    private static boolean warned = false;

    @Override
    public List<ItemStack> capture(ServerPlayer player) {
        List<ItemStack> items = new ArrayList<>();
        for (IItemHandlerModifiable handler : curioHandlers(player)) {
            for (int i = 0; i < handler.getSlots(); i++) {
                items.add(handler.getStackInSlot(i).copy());
            }
        }
        return items;
    }

    @Override
    public void restore(ServerPlayer player, List<ItemStack> items) {
        int index = 0;
        for (IItemHandlerModifiable handler : curioHandlers(player)) {
            for (int i = 0; i < handler.getSlots() && index < items.size(); i++, index++) {
                handler.setStackInSlot(i, items.get(index).copy());
            }
        }
        if (index < items.size()) {
            IAccessoryHelper.depositOverflow(player, items.subList(index, items.size()));
        }
    }

    @Override
    public void clear(ServerPlayer player) {
        for (IItemHandlerModifiable handler : curioHandlers(player)) {
            for (int i = 0; i < handler.getSlots(); i++) {
                handler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * Every equipped curio stack handler, ordered deterministically (sorted by slot identifier) so
     * capture/restore/clear all walk the same slots in the same order.
     */
    private static List<IItemHandlerModifiable> curioHandlers(ServerPlayer player) {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return List.of();
        }

        try {
            Class<?> apiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getInventory = apiClass.getMethod("getCuriosInventory", LivingEntity.class);
            Optional<?> curiosHandler = (Optional<?>) getInventory.invoke(null, player);
            if (curiosHandler.isEmpty()) {
                return List.of();
            }

            Method getCurios = curiosHandler.get().getClass().getMethod("getCurios");
            @SuppressWarnings("unchecked")
            Map<String, ?> curios = (Map<String, ?>) getCurios.invoke(curiosHandler.get());

            List<IItemHandlerModifiable> handlers = new ArrayList<>();
            for (Object stacksHandler : new TreeMap<>(curios).values()) {
                Method getStacks = stacksHandler.getClass().getMethod("getStacks");
                handlers.add((IItemHandlerModifiable) getStacks.invoke(stacksHandler));
            }
            return handlers;
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                Constants.LOG.warn("Curios is installed but its API didn't match what Building Dimension expects; accessory slots won't be snapshotted", e);
            }
            return List.of();
        }
    }
}
