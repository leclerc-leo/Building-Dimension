package net.fabricmc.BuildingDimension.Persistance;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public class PersistentDimensions {

    public static void save(Map<RegistryKey<World>, RegistryKey<World>> dimensions) {
        NbtCompound nbt = new NbtCompound();
        for (Map.Entry<RegistryKey<World>, RegistryKey<World>> entry : dimensions.entrySet()) {
            nbt.put(entry.getKey().getValue().toString(), worldKeyToNBT(entry.getValue()));
        }
        PersistenceManager.save("dimensions", nbt);
    }

    public static Map<RegistryKey<World>, RegistryKey<World>> load() {
        NbtCompound nbt = (NbtCompound) PersistenceManager.load("dimensions");
        if (nbt == null) return new HashMap<>();

        Map<RegistryKey<World>, RegistryKey<World>> dimensions = new HashMap<>();
        for (String key : nbt.getKeys()) {
            RegistryKey<World> dimension = nbtToWorldKey(nbt.get(key));
            if (dimension != null) {
                RegistryKey<World> world = RegistryKey.of(
                        Registry.WORLD_KEY,
                        new Identifier(key)
                );
                dimensions.put(world, dimension);
            }
        }
        return dimensions;
    }

    private static NbtElement worldKeyToNBT (RegistryKey<World> dimension) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("namespace", dimension.getValue().getNamespace());
        nbt.putString("path", dimension.getValue().getPath());
        return nbt;
    }

    private static RegistryKey<World> nbtToWorldKey (NbtElement nbt) {
        if (nbt == null) return null;
        NbtCompound compound = (NbtCompound) nbt;
        return RegistryKey.of(
                Registry.WORLD_KEY,
                new Identifier(
                        compound.getString("namespace"),
                        compound.getString("path")
                )
        );
    }
}
