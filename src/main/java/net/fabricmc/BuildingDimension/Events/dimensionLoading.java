package net.fabricmc.BuildingDimension.Events;

import net.fabricmc.BuildingDimension.BuildingDimension;
import net.fabricmc.BuildingDimension.Commands.SwitchDimension;
import net.fabricmc.BuildingDimension.Persistance.PersistentDimensions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class dimensionLoading {

    /**
     * This is called when the server starts.
     * It dynamically registers all the dimensions that are saved in the persistent data.
     * <p>
     * It's in place because Minecraft registers all the dimensions before the server starts with a JSON file.
     * But we want to be able to register dimensions from any mod, that's why we have to do it dynamically.
     */
    public static void init () {

        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            Map<RegistryKey<World>, RegistryKey<World>> d = new PersistentDimensions(server).load();
            SwitchDimension.DIMENSIONS = d;

            Set<RegistryKey<World>> dimensions = Objects.requireNonNull(d).keySet();

            for (RegistryKey<World> dimension : dimensions) {
                if (dimension.getValue().getNamespace().equals(BuildingDimension.MOD_ID)) {
                    continue;
                }

                SwitchDimension.createDimension(server, dimension);
            }
        });
    }
}
