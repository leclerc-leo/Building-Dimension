package net.buildingdimension.persistence;

import com.mojang.serialization.Codec;
import net.buildingdimension.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Remembers which source dimensions have had a building counterpart created.
 * <p>
 * The counterpart's own {@link ResourceKey} is always derivable from the source
 * (see {@link net.buildingdimension.dimension.BuildingDimensions#buildingKeyOf}), and its saved
 * chunk data lives on disk under its own dimension folder regardless of whether the in-memory
 * {@code ServerLevel} exists — so all that needs to survive a restart is this set of sources, used
 * to recreate their counterparts on the next boot (see {@code DimensionFactory#restoreAll}).
 */
public class DimensionRegistry extends SavedData {

    private static final Codec<DimensionRegistry> CODEC = ResourceKey.codec(Registries.DIMENSION)
        .listOf()
        .xmap(list -> new DimensionRegistry(new HashSet<>(list)), registry -> List.copyOf(registry.createdSources))
        .fieldOf("created_sources")
        .codec();

    public static final SavedDataType<DimensionRegistry> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Constants.MOD_ID, "dimension_registry"),
        DimensionRegistry::new,
        CODEC,
        null
    );

    private final Set<ResourceKey<Level>> createdSources;

    public DimensionRegistry() {
        this(new HashSet<>());
    }

    private DimensionRegistry(Set<ResourceKey<Level>> createdSources) {
        this.createdSources = createdSources;
    }

    public static DimensionRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Set<ResourceKey<Level>> createdSources() {
        return Set.copyOf(createdSources);
    }

    public void markCreated(ResourceKey<Level> source) {
        if (createdSources.add(source)) {
            setDirty();
        }
    }
}
