package net.buildingdimension.platform.services;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Snapshot hook for accessory-inventory mods (Trinkets on Fabric, Curios on NeoForge). Both are
 * optional dependencies with no compile-time API available to this project, so implementations
 * reach into them entirely via reflection, gated by {@link IPlatformHelper#isModLoaded(String)},
 * and fall back to an empty list whenever the mod isn't installed or its API doesn't match what
 * reflection expects.
 */
public interface IAccessoryHelper {

    /**
     * Copies every accessory slot's contents, in a stable order, so it can be restored later.
     * Returns an empty list if no accessory mod is installed.
     */
    List<ItemStack> capture(ServerPlayer player);

    /**
     * Writes previously captured items back into the accessory slots, in the same order they were
     * captured in. If the current slot layout is shorter than {@code items}, the extras are
     * dropped; if longer, the remaining slots are left untouched.
     */
    void restore(ServerPlayer player, List<ItemStack> items);

    /** Empties every accessory slot. */
    void clear(ServerPlayer player);
}
