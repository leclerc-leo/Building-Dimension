package net.fabricmc.BuildingDimension;

import com.moandjiezana.toml.Toml;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Configs {

    private static final String CONFIG_FILE_NAME = "building_dimension.toml";
    public static final String CONFIG_FILE_PATH = FabricLoader.getInstance().getConfigDir() + "/" + CONFIG_FILE_NAME;
    private static final File CONFIG_FILE = new File(CONFIG_FILE_PATH);

    public static int MAX_RADIUS = 8;
    public static boolean OP_SYNC = true;

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            generate();
            return;
        }

        Toml toml = new Toml().read(CONFIG_FILE);

        MAX_RADIUS = Integer.max(1, Integer.min(32, toml.getLong("max_radius").intValue()));
        OP_SYNC = toml.getBoolean("op_sync");
    }

    private static void generate() {
        try {
            if (!CONFIG_FILE.exists()) {
                if (!CONFIG_FILE.createNewFile()) {
                    BuildingDimension.LOGGER.error("Failed to create config file!");
                    return;
                }
            }

            FileWriter writer = new FileWriter(CONFIG_FILE);

            writer.write("# Max radius when syncing chunks (1 ~ 32)\n");
            writer.write("max_radius = 8\n\n");

            writer.write("# Whether only OPs can sync chunks (true : only OPs can sync, false : everyone can sync)\n");
            writer.write("op_sync = true\n\n");

            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
