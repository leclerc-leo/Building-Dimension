package net.buildingdimension.dimension;

import net.buildingdimension.config.BuildingDimensionConfig;
import net.buildingdimension.config.BuildingDimensionConfig.WeatherMode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.WeatherData;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Weather is server-wide in vanilla — every {@link ServerLevel#getWeatherData()} call delegates to
 * the same single {@code MinecraftServer#getWeatherData()} instance, so every dimension always has
 * identical weather. {@code BuildingDimensionWeatherMixin} overrides that per-level for building
 * dimensions when {@link BuildingDimensionConfig#buildingDimensionWeather()} isn't {@code NORMAL},
 * redirecting {@code getWeatherData()} to a per-level instance held here instead.
 * <p>
 * The override is re-applied on every read (see {@link #overrideFor}) rather than set once, so nothing
 * can make it drift — a player-issued {@code /weather} command, or vanilla's own weather-cycle tick
 * logic mutating the timers on this object, is corrected on the very next read.
 */
public final class BuildingDimensionWeather {

    private static final Map<ServerLevel, WeatherData> OVERRIDES = new WeakHashMap<>();

    private BuildingDimensionWeather() {
    }

    /**
     * Returns the forced weather state for {@code level}, or {@code null} if it isn't a building
     * dimension or the config is {@code NORMAL} (meaning: don't override, use the server's own).
     */
    public static WeatherData overrideFor(ServerLevel level) {
        WeatherMode mode = BuildingDimensionConfig.buildingDimensionWeather();
        if (mode == WeatherMode.NORMAL || !BuildingDimensions.isBuildingDimension(level.dimension())) {
            OVERRIDES.remove(level);
            return null;
        }

        WeatherData data = OVERRIDES.computeIfAbsent(level, l -> new WeatherData());
        applyMode(data, mode);
        return data;
    }

    private static void applyMode(WeatherData data, WeatherMode mode) {
        switch (mode) {
            case ALWAYS_CLEAR -> {
                data.setClearWeatherTime(Integer.MAX_VALUE);
                data.setRainTime(0);
                data.setThunderTime(0);
                data.setRaining(false);
                data.setThundering(false);
            }
            case ALWAYS_RAIN -> {
                data.setClearWeatherTime(0);
                data.setRainTime(Integer.MAX_VALUE);
                data.setThunderTime(0);
                data.setRaining(true);
                data.setThundering(false);
            }
            case ALWAYS_THUNDER -> {
                data.setClearWeatherTime(0);
                data.setRainTime(Integer.MAX_VALUE);
                data.setThunderTime(Integer.MAX_VALUE);
                data.setRaining(true);
                data.setThundering(true);
            }
            case NORMAL -> {
                // unreachable: overrideFor() already returns null for NORMAL before this is called
            }
        }
    }

    /** Drops every cached override. Called on server stop, since {@link ServerLevel} keys don't survive it. */
    public static void reset() {
        OVERRIDES.clear();
    }
}
