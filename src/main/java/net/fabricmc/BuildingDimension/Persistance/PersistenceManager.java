package net.fabricmc.BuildingDimension.Persistance;

import net.fabricmc.BuildingDimension.BuildingDimension;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PersistenceManager extends PersistentState {

    public static NbtCompound saved_data = new NbtCompound();

    /**
     * Method called when the server stops.
     *
     * @param nbt The NBT to save the data to
     * @return The NBT to save the data to
     */
    @Override
    public NbtCompound writeNbt(@NotNull NbtCompound nbt) {
        BuildingDimension.log("Saving data: " + saved_data);
        return nbt.copyFrom(saved_data);
    }

    /**
     * Method called when the server starts.
     *
     * @param nbt The NBT to load the data from
     *            (this is the same NBT as the one returned by {@link PersistenceManager#writeNbt(NbtCompound)})
     * @return The PersistenceManager instance
     */
    private static @NotNull PersistenceManager createFromNbt(@NotNull NbtCompound nbt) {
        saved_data = nbt.copyFrom(nbt);
        return new PersistenceManager();
    }

    /**
     * I have no idea what this does.
     *
     * @param server The server instance
     */
    public static PersistenceManager getSavedData(@NotNull MinecraftServer server) {
        PersistentStateManager persistentStateManager = Objects.requireNonNull(server.
                getWorld(World.OVERWORLD)).getPersistentStateManager();

        return persistentStateManager.getOrCreate(
                PersistenceManager::createFromNbt,
                PersistenceManager::new,
                BuildingDimension.MOD_ID);
    }

    /**
     * Saves the NBT data to the saved_data map.
     * Which is then saved to the disk by {@link PersistenceManager#writeNbt(NbtCompound)}
     *
     * @param id The id of the data
     * @param nbt The NBT data
     */
    public void save(@NotNull String id, @NotNull NbtElement nbt) {
        saved_data.put(id, nbt);
        this.markDirty();
    }

    /**
     * Loads the NBT data from the saved_data map.
     * Which is then loaded from the disk by {@link PersistenceManager#createFromNbt(NbtCompound)}
     *
     * @param id The id of the data
     * @return The NBT data
     */
    public NbtElement load(@NotNull String id) {
        return saved_data.get(id);
    }
}
