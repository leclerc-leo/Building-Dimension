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

    @Override
    public NbtCompound writeNbt(@NotNull NbtCompound nbt) {
        BuildingDimension.log("Saving Persistance: " + saved_data);

        return nbt.copyFrom(saved_data);
    }

    public static @NotNull PersistenceManager createFromNbt(@NotNull NbtCompound nbt) {
        saved_data = nbt.copyFrom(nbt);

        BuildingDimension.log("Loading Persistance: " + saved_data);
        return new PersistenceManager();
    }

    public static void getSavedData(@NotNull MinecraftServer server) {
        PersistentStateManager persistentStateManager = Objects.requireNonNull(server.
                getWorld(World.OVERWORLD)).getPersistentStateManager();

        PersistenceManager data = persistentStateManager.getOrCreate(
                PersistenceManager::createFromNbt,
                PersistenceManager::new,
            BuildingDimension.MOD_ID);

        data.markDirty();
    }

    public static void save(@NotNull String id, @NotNull NbtElement nbt) {
        saved_data.put(id, nbt);
    }

    public static NbtElement load(@NotNull String id) {
        return saved_data.get(id);
    }
}
