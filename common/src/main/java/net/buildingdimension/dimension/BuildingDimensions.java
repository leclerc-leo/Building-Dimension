package net.buildingdimension.dimension;

import net.buildingdimension.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Maps a dimension to its creative "building" counterpart and back.
 * <p>
 * A building dimension always lives in the {@code building_dimension} namespace and its path is
 * {@code <source namespace>_<source path>} (e.g. {@code minecraft:overworld} maps to
 * {@code building_dimension:minecraft_overworld}). This makes the forward mapping a pure
 * computation, and the reverse mapping a scan over the server's loaded levels, so nothing about
 * the mapping itself needs to be persisted.
 * <p>
 * Building dimensions are not declared anywhere — they are created on demand by
 * {@link DimensionFactory}, cloning whatever dimension type and chunk generator the source
 * dimension uses. This is what lets modded dimensions get a counterpart automatically, with no
 * per-mod configuration.
 */
public final class BuildingDimensions {

    private BuildingDimensions() {
    }

    /**
     * Checks whether a dimension is one of the creative building dimensions.
     */
    public static boolean isBuildingDimension(ResourceKey<Level> dimension) {
        return dimension.identifier().getNamespace().equals(Constants.MOD_ID);
    }

    /**
     * Computes the building counterpart key of a source dimension.
     * The dimension is not guaranteed to exist on the server.
     */
    public static ResourceKey<Level> buildingKeyOf(ResourceKey<Level> source) {
        Identifier id = source.identifier();
        return ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, id.getNamespace() + "_" + id.getPath())
        );
    }

    /**
     * Finds the source dimension a building dimension was created from, by scanning
     * the server's levels for the one whose computed counterpart matches.
     */
    public static Optional<ResourceKey<Level>> sourceOf(MinecraftServer server, ResourceKey<Level> building) {
        for (ResourceKey<Level> candidate : server.levelKeys()) {
            if (!isBuildingDimension(candidate) && buildingKeyOf(candidate).equals(building)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves the dimension a player in {@code from} should be sent to by /switch, creating the
     * building counterpart on first use if {@code from} is not itself a building dimension.
     */
    public static Optional<ResourceKey<Level>> counterpartOf(MinecraftServer server, ResourceKey<Level> from) {
        if (isBuildingDimension(from)) {
            return sourceOf(server, from);
        }

        ServerLevel source = server.getLevel(from);
        if (source == null) {
            return Optional.empty();
        }

        return Optional.of(DimensionFactory.getOrCreate(server, source));
    }
}
