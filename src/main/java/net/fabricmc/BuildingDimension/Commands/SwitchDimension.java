package net.fabricmc.BuildingDimension.Commands;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.BuildingDimension.BuildingDimension;
import net.fabricmc.BuildingDimension.Persistance.PersistentDimensions;
import net.fabricmc.BuildingDimension.Persistance.PersistentPlayer;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;
import org.jetbrains.annotations.NotNull;
import qouteall.q_misc_util.api.DimensionAPI;

import java.util.*;

public class SwitchDimension {

    /**
     * A map of a dimension to it's building counterpart and vice versa.
     * This allows us to easily find the destination dimension when switching dimensions.
     */
    public static Map<RegistryKey<World>, RegistryKey<World>> DIMENSIONS = new HashMap<>();

    /**
     * A map of a player to the last time they switched dimensions.
     * This is used to prevent players from switching dimensions too quickly.
     */
    private static final Map<UUID, Long> last_switch = new HashMap<>();

    /**
     * A set of players that are allowed to switch dimensions.
     * This is used to prevent players from leaving the building dimension.
     * They are added to the set when they use the /switch command and removed when they are teleported.
     * <p>
     * see {@link net.fabricmc.BuildingDimension.Mixins.ServerPlayerEntityMixin} for the logic behind blocking players from leaving the building dimension
     * It's particularly useful when the player is in dimensions from other mods and those mods use a different way to exit the dimension.
     * For example, the Custom Portals mod uses a custom portal block to exit the dimension, or the Bee Dimension mod uses a bee hive.
     */
    public static final Set<UUID> allowed_switching = new HashSet<>();

    public static int switch_dim(@NotNull CommandContext<ServerCommandSource> context) {
        try {
            ServerCommandSource source = context.getSource();
            ServerPlayerEntity player = source.getPlayer();

            if (player == null) {
                source.sendMessage(Text.literal("You must be a player to use this command"));
                return -1;
            }

            if (isOnCooldown(player)) {
                source.sendMessage(Text.literal("You must wait 5 seconds between dimension switches"));
                return -1;
            }

            if (setupDimensions(source)) {
                source.sendMessage(Text.literal("Error creating new dimension"));
                return -1;
            }

            PersistentPlayer persistent_player = new PersistentPlayer(source.getServer());
            RegistryKey<World> target_dim = DIMENSIONS.get(source.getWorld().getRegistryKey());
            BuildingDimension.log("Switching dimension to : " + target_dim.getValue());

            persistent_player.save(player, source.getWorld().getRegistryKey());

            persistent_player.cleanPlayer(player);

            Vec3d position;
            GameMode gamemode;
            if (player.getWorld().getRegistryKey().getValue().getNamespace().equals(BuildingDimension.MOD_ID)) {
                BuildingDimension.log("Loading from save");
                position = persistent_player.getPosition(player);
                gamemode = persistent_player.getGamemode(player);

            } else {
                BuildingDimension.log("Saving to save");
                position = player.getPos();
                gamemode = GameMode.CREATIVE;
            }

            player.changeGameMode(gamemode);
            TeleportTarget target = new TeleportTarget(
                    position,
                    new Vec3d(0, 0, 0),
                    player.getYaw(),
                    player.getPitch()
            );

            BuildingDimension.log("Teleporting player to : " + target_dim.getValue());
            BuildingDimension.log("Teleporting player to : " + target.position.toString());
            BuildingDimension.log("Switching player gamemode to : " + gamemode.toString());

            allowed_switching.add(player.getUuid());

            FabricDimensions.teleport(
                    player,
                    source.getServer().getWorld(target_dim),
                    target
            );

            allowed_switching.remove(player.getUuid());

            persistent_player.load(player, target_dim);

            return 1;
        } catch (Exception e) {
            BuildingDimension.logError("Failed to switch dimension: ", e, context.getSource());
            return -1;
        }
    }

    /**
     * Sets up the dimensions if they haven't been set up yet
     * And adds them to the DIMENSIONS map if they haven't been added yet
     *
     * @param source The command source
     * @return false if the dimensions were already set up, true otherwise
     */
    private static boolean setupDimensions(@NotNull ServerCommandSource source) {
        if (!DIMENSIONS.containsKey(source.getWorld().getRegistryKey())) {

            ServerWorld world = source.getWorld();

            BuildingDimension.log("Creating new dimension : " + world.getRegistryKey().getValue().getPath());

            if(createDimension(
                    source.getServer(),
                    world.getRegistryKey()
            )) return true;

            RegistryKey<World> building_dimension = RegistryKey.of(
                    RegistryKeys.WORLD,
                    new Identifier(BuildingDimension.MOD_ID, world.getRegistryKey().getValue().getPath())
            );

            DIMENSIONS.put(
                    world.getRegistryKey(),
                    building_dimension
            );
            DIMENSIONS.put(
                    building_dimension,
                    world.getRegistryKey()
            );

            new PersistentDimensions(source.getServer()).save(DIMENSIONS);
        }

        return false;
    }

    /**
     * Creates a new dimension with the same options and ChunkGenerator as the given dimension
     *
     * @param server The server
     * @param dimension The dimension to create
     * @return true if the dimension was created, false otherwise
     */
    public static boolean createDimension (MinecraftServer server, RegistryKey<World> dimension) {
        if (dimension.getValue().getNamespace().equals(BuildingDimension.MOD_ID)) {
            return false;
        }

        ServerWorld world = server.getWorld(dimension);

        if (world == null) {
            BuildingDimension.logError("Failed to load world: ", new Exception(), null);
            return true;
        }

        try {
            DimensionAPI.addDimensionDynamically(
                    new Identifier(BuildingDimension.MOD_ID, dimension.getValue().getPath()),
                    new DimensionOptions(
                            world.getDimensionEntry(),
                            world.getChunkManager().getChunkGenerator()
                    )
            );
        } catch (Exception e) {
            // There is usually an error when trying to create a dimension that already exists when the server starts
            // But when a player tries to create a dimension that already exists, it doesn't throw an error
            // DimensionAPI uses a different method in its latest version for Minecraft 1.20.4 which should fix this

            BuildingDimension.logError("Failed to create dimension: ", e, null);
            return true;
        }

        BuildingDimension.log("Loaded building dimension equivalent of : " + dimension.getValue());

        return false;
    }

    /**
     * Checks if the player is on cooldown when switching dimensions
     *
     * @param player The player to check
     * @return true if the player is on cooldown, false otherwise
     */
    private static boolean isOnCooldown(@NotNull ServerPlayerEntity player) {
        if (last_switch.containsKey(player.getUuid())) {
            long last_switch_time = last_switch.get(player.getUuid());
            long current_time = new Date().getTime();

            return current_time - last_switch_time < 5000;
        } else {
            last_switch.put(player.getUuid(), new Date().getTime());
        }
        return false;
    }
}