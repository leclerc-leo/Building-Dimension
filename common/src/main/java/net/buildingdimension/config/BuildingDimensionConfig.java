package net.buildingdimension.config;

import net.buildingdimension.Constants;
import net.buildingdimension.platform.Services;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * A handful of settings don't warrant a loader-specific config framework, so this is a plain
 * properties file shared by both loaders via {@link net.buildingdimension.platform.services.IPlatformHelper#getConfigDirectory()}.
 * <p>
 * Reloadable at runtime via {@code /buildingdimension reload} (see {@code ConfigCommand}), so
 * server admins don't need a restart to pick up a config edit.
 */
public final class BuildingDimensionConfig {

    /**
     * How a building dimension's weather is forced, independent of the real weather cycle
     * elsewhere on the server (see {@code BuildingDimensionWeatherMixin}).
     */
    public enum WeatherMode {
        NORMAL, ALWAYS_CLEAR, ALWAYS_RAIN, ALWAYS_THUNDER
    }

    private static final String FILE_NAME = Constants.MOD_ID + ".properties";
    private static final String GENERATE_STRUCTURES_KEY = "generateStructuresInBuildingDimensions";
    private static final String DISABLE_MOB_GRIEFING_KEY = "disableMobGriefing";
    private static final String DISABLE_MOB_SPAWNING_KEY = "disableMobSpawning";
    private static final String SYNC_MAX_RADIUS_KEY = "syncMaxRadius";
    private static final String SWITCH_REQUIRE_OP_KEY = "switchRequireOp";
    private static final String SWITCH_REQUIRE_WHITELIST_KEY = "switchRequireWhitelist";
    private static final String SWITCH_DENIED_USE_SPECTATOR_KEY = "switchDeniedPlayersUseSpectator";
    private static final String BUILDING_DIMENSION_WEATHER_KEY = "buildingDimensionWeather";

    private static volatile boolean generateStructuresInBuildingDimensions = false;
    private static volatile boolean disableMobGriefing = true;
    private static volatile boolean disableMobSpawning = true;
    private static volatile int syncMaxRadius = 8;
    private static volatile boolean switchRequireOp = false;
    private static volatile boolean switchRequireWhitelist = false;
    private static volatile boolean switchDeniedPlayersUseSpectator = false;
    private static volatile WeatherMode buildingDimensionWeather = WeatherMode.NORMAL;

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
     * The largest {@code /sync} radius a non-operator may request; operators are never limited by
     * this (see {@code SyncCommand}). Bounds how many chunk-pairs a single command can queue, since
     * an unbounded radius lets any player flood the chunk system and fill the disk.
     */
    public static int syncMaxRadius() {
        return syncMaxRadius;
    }

    /**
     * Whether {@code /switch} requires operator permission (permission level 2). Combined with
     * {@link #switchRequireWhitelist()}: if both are enabled, a player needs to satisfy both.
     */
    public static boolean switchRequireOp() {
        return switchRequireOp;
    }

    /**
     * Whether {@code /switch} requires the player to be on {@code building_dimension_whitelist.txt}.
     * Combined with {@link #switchRequireOp()}: if both are enabled, a player needs to satisfy both.
     */
    public static boolean switchRequireWhitelist() {
        return switchRequireWhitelist;
    }

    /**
     * Whether a player who fails the {@code /switch} permission check still gets to enter the
     * building dimension, in spectator mode, instead of being turned away outright — so they can
     * look around at what's been built without being able to touch anything. If disabled (the
     * default), a denied player's {@code /switch} simply fails with a message.
     */
    public static boolean switchDeniedPlayersUseSpectator() {
        return switchDeniedPlayersUseSpectator;
    }

    /**
     * How a building dimension's weather is forced. {@code NORMAL} leaves it exactly like vanilla
     * (which today means every dimension shares one server-wide weather clock); any other mode
     * gives building dimensions their own independent, permanently-set weather.
     */
    public static WeatherMode buildingDimensionWeather() {
        return buildingDimensionWeather;
    }

    /**
     * Loads the config from disk, creating it with defaults on first run. Safe to call again later
     * (see {@code ConfigCommand}'s {@code /buildingdimension reload}) to pick up manual edits.
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
        syncMaxRadius = parsePositiveInt(properties.getProperty(SYNC_MAX_RADIUS_KEY), 8);
        switchRequireOp = Boolean.parseBoolean(
            properties.getProperty(SWITCH_REQUIRE_OP_KEY, "false"));
        switchRequireWhitelist = Boolean.parseBoolean(
            properties.getProperty(SWITCH_REQUIRE_WHITELIST_KEY, "false"));
        switchDeniedPlayersUseSpectator = Boolean.parseBoolean(
            properties.getProperty(SWITCH_DENIED_USE_SPECTATOR_KEY, "false"));
        buildingDimensionWeather = parseWeatherMode(properties.getProperty(BUILDING_DIMENSION_WEATHER_KEY));

        properties.setProperty(GENERATE_STRUCTURES_KEY, String.valueOf(generateStructuresInBuildingDimensions));
        properties.setProperty(DISABLE_MOB_GRIEFING_KEY, String.valueOf(disableMobGriefing));
        properties.setProperty(DISABLE_MOB_SPAWNING_KEY, String.valueOf(disableMobSpawning));
        properties.setProperty(SYNC_MAX_RADIUS_KEY, String.valueOf(syncMaxRadius));
        properties.setProperty(SWITCH_REQUIRE_OP_KEY, String.valueOf(switchRequireOp));
        properties.setProperty(SWITCH_REQUIRE_WHITELIST_KEY, String.valueOf(switchRequireWhitelist));
        properties.setProperty(SWITCH_DENIED_USE_SPECTATOR_KEY, String.valueOf(switchDeniedPlayersUseSpectator));
        properties.setProperty(BUILDING_DIMENSION_WEATHER_KEY, buildingDimensionWeather.name());
        save(file, properties);
    }

    private static int parsePositiveInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static WeatherMode parseWeatherMode(String value) {
        if (value == null) {
            return WeatherMode.NORMAL;
        }
        try {
            return WeatherMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            Constants.LOG.warn("Unknown {} value '{}', falling back to NORMAL", BUILDING_DIMENSION_WEATHER_KEY, value);
            return WeatherMode.NORMAL;
        }
    }

    /**
     * Test-only: overrides the in-memory /switch access settings without touching disk, so
     * GameTest bodies can exercise {@code SwitchAccess} without a real config file round-trip.
     */
    public static void setSwitchAccessForTest(boolean requireOp, boolean requireWhitelist, boolean deniedUseSpectator) {
        switchRequireOp = requireOp;
        switchRequireWhitelist = requireWhitelist;
        switchDeniedPlayersUseSpectator = deniedUseSpectator;
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
