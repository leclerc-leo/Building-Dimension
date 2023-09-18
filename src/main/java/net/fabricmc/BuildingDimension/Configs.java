package net.fabricmc.BuildingDimension;

import com.moandjiezana.toml.Toml;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.function.Function;

public class Configs {

    private static final String CONFIG_FILE_NAME = "building_dimension.toml";
    public static final String CONFIG_FILE_PATH = FabricLoader.getInstance().getConfigDir() + "/" + CONFIG_FILE_NAME;
    private static final File CONFIG_FILE = new File(CONFIG_FILE_PATH);

    public static int MAX_RADIUS = 8;
    public static boolean ONLY_OP = false;
    public static boolean NON_OPS_SPECTATOR = true;
    public static boolean OP_SYNC = false;

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            generate();
            return;
        }

        Toml toml = new Toml().read(CONFIG_FILE);

        check_config(toml, "max_radius", (config_value) ->
            MAX_RADIUS = Integer.max(1, Integer.min(32, toml.getLong(config_value).intValue()))
        );
        check_config(toml, "only_ops", (config_value) ->
            ONLY_OP = toml.getBoolean(config_value)
        );
        check_config(toml, "non_ops_spectators", (config_value) ->
            NON_OPS_SPECTATOR = toml.getBoolean(config_value)
        );
        check_config(toml, "op_sync", (config_value) ->
            OP_SYNC = toml.getBoolean(config_value)
        );
    }

    private static void generate() {
        try {
            Files.copy(
                    Objects.requireNonNull(BuildingDimension.class.getResourceAsStream("/assets/config/building_dimension.toml")),
                    CONFIG_FILE.toPath()
            );

        } catch (IOException e) {
            BuildingDimension.LOGGER.error("Failed to generate config file!");
        }
    }

    private static void check_config (@NotNull Toml toml, String config_value, Function<String, Object> apply) {
        if (toml.contains(config_value)) {
            try {
                apply.apply(config_value);

            } catch (Exception e) {
                BuildingDimension.LOGGER.error("Failed to load config value: " + config_value);
            }
        }
    }
}
