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

    public static void init () {

        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            Map<RegistryKey<World>, RegistryKey<World>> d = PersistentDimensions.load();
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
