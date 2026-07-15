package net.buildingdimension.config;

import net.buildingdimension.Constants;
import net.buildingdimension.platform.Services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The player names allowed to use {@code /switch} when {@link BuildingDimensionConfig#switchRequireWhitelist()}
 * is enabled, one per line in {@code building_dimension_whitelist.txt}. Names are matched
 * case-insensitively, the same way vanilla's own whitelist does.
 */
public final class SwitchWhitelist {

    private static final String FILE_NAME = Constants.MOD_ID + "_whitelist.txt";

    private static volatile Set<String> names = Set.of();

    private SwitchWhitelist() {
    }

    public static boolean contains(String playerName) {
        return names.contains(playerName.toLowerCase(Locale.ROOT));
    }

    public static List<String> list() {
        return names.stream().sorted(Comparator.naturalOrder()).toList();
    }

    public static boolean add(String playerName) {
        Set<String> updated = new LinkedHashSet<>(names);
        boolean added = updated.add(playerName.toLowerCase(Locale.ROOT));
        if (added) {
            names = updated;
            save();
        }
        return added;
    }

    public static boolean remove(String playerName) {
        Set<String> updated = new LinkedHashSet<>(names);
        boolean removed = updated.remove(playerName.toLowerCase(Locale.ROOT));
        if (removed) {
            names = updated;
            save();
        }
        return removed;
    }

    /**
     * Loads the whitelist from disk, creating an empty file on first run.
     */
    public static void load() {
        Path file = file();
        if (!Files.exists(file)) {
            save(file, Set.of());
            names = Set.of();
            return;
        }

        try {
            Set<String> loaded = new LinkedHashSet<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    loaded.add(trimmed.toLowerCase(Locale.ROOT));
                }
            }
            names = loaded;
        } catch (IOException e) {
            Constants.LOG.error("Failed to read {}, falling back to an empty whitelist", file, e);
            names = Set.of();
        }
    }

    private static void save() {
        save(file(), names);
    }

    private static void save(Path file, Set<String> names) {
        try {
            Files.createDirectories(file.getParent());
            String content = "# One player name per line. Managed by /buildingdimension whitelist, or edit and /buildingdimension reload.\n"
                + String.join("\n", names.stream().sorted(Comparator.naturalOrder()).toList());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.LOG.error("Failed to write {}", file, e);
        }
    }

    private static Path file() {
        return Services.PLATFORM.getConfigDirectory().resolve(FILE_NAME);
    }
}
