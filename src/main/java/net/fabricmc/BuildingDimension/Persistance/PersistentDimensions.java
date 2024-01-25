package net.fabricmc.BuildingDimension.Persistance;

import net.fabricmc.BuildingDimension.BuildingDimension;
import net.fabricmc.BuildingDimension.Events.dimensionLoading;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class PersistentDimensions {

    private final PersistenceManager manager;

    public PersistentDimensions(@NotNull MinecraftServer server) {
        this.manager = PersistenceManager.getSavedData(server);
    }

    /**
     * Saves the dimensions to the disk to be dynamically registered when the server starts.
     * see {@link dimensionLoading#init()} for the registration logic
     *
     * @param dimensions The dimensions to save
     */
    public void save(Map<RegistryKey<World>, RegistryKey<World>> dimensions) {
        NbtCompound nbt = new NbtCompound();
        for (Map.Entry<RegistryKey<World>, RegistryKey<World>> entry : dimensions.entrySet()) {
            nbt.put(entry.getKey().getValue().toString(), worldKeyToNBT(entry.getValue()));
        }
        manager.save("dimensions", nbt);
    }

    /**
     * Loads the dimensions from the disk.
     * see {@link dimensionLoading#init()} for the registration logic
     *
     * @return The dimensions
     */
    public Map<RegistryKey<World>, RegistryKey<World>> load() {
        NbtCompound nbt = (NbtCompound) manager.load("dimensions");
        if (nbt == null) return new HashMap<>();

        Map<RegistryKey<World>, RegistryKey<World>> dimensions = new HashMap<>();
        for (String key : nbt.getKeys()) {
            RegistryKey<World> dimension = nbtToWorldKey(nbt.get(key));
            if (dimension != null) {
                RegistryKey<World> world = RegistryKey.of(
                        RegistryKeys.WORLD,
                        new Identifier(key)
                );
                dimensions.put(world, dimension);
            } else {
                BuildingDimension.log("Failed to load dimension: " + key);
            }
        }
        return dimensions;
    }

    /**
     * Converts a RegistryKey<World> to NBT
     *
     * @param dimension The dimension to convert
     * @return The NBT
     */
    private static NbtElement worldKeyToNBT (RegistryKey<World> dimension) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("namespace", dimension.getValue().getNamespace());
        nbt.putString("path", dimension.getValue().getPath());
        return nbt;
    }

    /**
     * Converts NBT to a RegistryKey<World>
     *
     * @param nbt The NBT to convert
     * @return The RegistryKey<World>
     */
    private static RegistryKey<World> nbtToWorldKey (NbtElement nbt) {
        if (nbt == null) return null;
        NbtCompound compound = (NbtCompound) nbt;
        return RegistryKey.of(
                RegistryKeys.WORLD,
                new Identifier(
                        compound.getString("namespace"),
                        compound.getString("path")
                )
        );
    }
}
