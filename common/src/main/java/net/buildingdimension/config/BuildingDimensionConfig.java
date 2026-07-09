package net.buildingdimension.config;

import net.buildingdimension.Constants;
import net.buildingdimension.platform.Services;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * A single boolean doesn't warrant a loader-specific config framework, so this is a plain
 * properties file shared by both loaders via {@link net.buildingdimension.platform.services.IPlatformHelper#getConfigDirectory()}.
 */
public final class BuildingDimensionConfig {

    private static final String FILE_NAME = Constants.MOD_ID + ".properties";
    private static final String GENERATE_STRUCTURES_KEY = "generateStructuresInBuildingDimensions";
    private static final String DISABLE_MOB_GRIEFING_KEY = "disableMobGriefing";
    private static final String DISABLE_MOB_SPAWNING_KEY = "disableMobSpawning";

    private static volatile boolean generateStructuresInBuildingDimensions = false;
    private static volatile boolean disableMobGriefing = true;
    private static volatile boolean disableMobSpawning = true;

    private BuildingDimensionConfig() {
    }

    /**
     * Whether vanilla/modded structures (villages, strongholds, etc.) are allowed to generate
     * inside building dimensions. Disabled by default: building dimensions exist to plan builds
     * around the player's own terrain, so unrelated structures and their loot/mobs would just be
     * clutter.
     */
    public static boolean generateStructuresInBuildingDimensions() {
        return generateStructuresInBuildingDimensions;
    }

    /**
     * Whether the server should force the {@code mobGriefing} game rule off on startup. Enabled by
     * default, since griefing (creepers, endermen, etc.) undoes builds; game rules are server-wide
     * in vanilla, so this affects every dimension, not just building ones.
     */
    public static boolean disableMobGriefing() {
        return disableMobGriefing;
    }

    /**
     * Whether the server should force the {@code doMobSpawning} game rule off on startup. Enabled
     * by default, for the same reason as {@link #disableMobGriefing()}: server-wide, not just
     * building dimensions.
     */
    public static boolean disableMobSpawning() {
        return disableMobSpawning;
    }

    /**
     * Loads the config from disk, creating it with defaults on first run.
     */
    public static void load() {
        Path file = Services.PLATFORM.getConfigDirectory().resolve(FILE_NAME);
        Properties properties = new Properties();

        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                Constants.LOG.error("Failed to read {}, falling back to defaults", file, e);
            }
        }

        generateStructuresInBuildingDimensions = Boolean.parseBoolean(
            properties.getProperty(GENERATE_STRUCTURES_KEY, "false"));
        disableMobGriefing = Boolean.parseBoolean(
            properties.getProperty(DISABLE_MOB_GRIEFING_KEY, "true"));
        disableMobSpawning = Boolean.parseBoolean(
            properties.getProperty(DISABLE_MOB_SPAWNING_KEY, "true"));

        properties.setProperty(GENERATE_STRUCTURES_KEY, String.valueOf(generateStructuresInBuildingDimensions));
        properties.setProperty(DISABLE_MOB_GRIEFING_KEY, String.valueOf(disableMobGriefing));
        properties.setProperty(DISABLE_MOB_SPAWNING_KEY, String.valueOf(disableMobSpawning));
        save(file, properties);
    }

    private static void save(Path file, Properties properties) {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, Constants.MOD_NAME + " config");
            }
        } catch (IOException e) {
            Constants.LOG.error("Failed to write {}", file, e);
        }
    }
}
