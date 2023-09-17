package net.fabricmc.BuildingDimension.Events;

import net.fabricmc.BuildingDimension.BuildingDimension;
import net.fabricmc.BuildingDimension.Commands.SwitchDimension;
import net.fabricmc.BuildingDimension.Persistance.PersistentDimensions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class dimensionLoading {

    public static void init () {
        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            Fantasy fantasy = Fantasy.get(server);

            Map<RegistryKey<World>, RegistryKey<World>> d = PersistentDimensions.load();
            SwitchDimension.DIMENSIONS = d;

            Set<RegistryKey<World>> dimensions = Objects.requireNonNull(d).keySet();

            for (RegistryKey<World> dimension : dimensions) {
                if (dimension.getValue().getNamespace().equals(BuildingDimension.MOD_ID)) {
                    continue;
                }

                ServerWorld world = server.getWorld(dimension);

                if (world == null) {
                    BuildingDimension.LOGGER.error("Could not find world for dimension " + dimension.getValue());
                    continue;
                }

                RuntimeWorldConfig worldConfig = new RuntimeWorldConfig();
                worldConfig.setGenerator(world.getChunkManager().getChunkGenerator());
                worldConfig.setSeed(world.getSeed());

                fantasy.getOrOpenPersistentWorld(
                        new Identifier(
                                BuildingDimension.MOD_ID,
                                world.getRegistryKey().getValue().getPath()
                        ),
                        worldConfig
                );

                BuildingDimension.log("Loaded dimension : " + dimension.getValue());
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
            PersistentDimensions.save(SwitchDimension.DIMENSIONS)
        );
    }
}
