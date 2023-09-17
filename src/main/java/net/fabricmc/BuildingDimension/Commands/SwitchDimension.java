package net.fabricmc.BuildingDimension.Commands;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.BuildingDimension.BuildingDimension;
import net.fabricmc.BuildingDimension.Persistance.PersistentPlayer;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

import java.util.*;

public class SwitchDimension {

    public static Map<RegistryKey<World>, RegistryKey<World>> DIMENSIONS = new HashMap<>();

    private static final Map<UUID, Long> last_switch = new HashMap<>();

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

            RegistryKey<World> target_dim = DIMENSIONS.get(source.getWorld().getRegistryKey());
            BuildingDimension.log("Switching dimension to : " + target_dim.getValue());

            PersistentPlayer.save(player, source.getWorld().getRegistryKey());

            PersistentPlayer.cleanPlayer(player);

            Vec3d position;
            GameMode gamemode;
            if (player.getWorld().getRegistryKey().getValue().getNamespace().equals(BuildingDimension.MOD_ID)) {
                BuildingDimension.log("Loading from save");
                position = PersistentPlayer.getPosition(player);
                gamemode = PersistentPlayer.getGamemode(player);

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
            BuildingDimension.log("Teleporting player to : " + target);
            BuildingDimension.log("Switching player gamemode to : " + gamemode.toString());

            FabricDimensions.teleport(
                    player,
                    source.getServer().getWorld(target_dim),
                    target
            );

            PersistentPlayer.load(player, target_dim);

            return 1;
        } catch (Exception e) {
            BuildingDimension.log("Error switching dimension : " + e.getMessage());

            StackTraceElement[] stackTrace = e.getStackTrace();
            for (StackTraceElement element : stackTrace) {
                BuildingDimension.log(element.toString());
            }

            return -1;
        }
    }

    private static boolean setupDimensions(@NotNull ServerCommandSource source) {
        if (!DIMENSIONS.containsKey(source.getWorld().getRegistryKey())) {
            Fantasy fantasy = Fantasy.get(source.getServer());
            ServerWorld world = source.getWorld();

            BuildingDimension.log("Creating new dimension : " + world.getRegistryKey().getValue().getPath());

            RuntimeWorldConfig worldConfig = new RuntimeWorldConfig();
            worldConfig.setGenerator(world.getChunkManager().getChunkGenerator());
            worldConfig.setSeed(world.getSeed());

            try {
                RuntimeWorldHandle worldHandle = fantasy.getOrOpenPersistentWorld(
                        new Identifier(
                                BuildingDimension.MOD_ID,
                                world.getRegistryKey().getValue().getPath()
                        ),
                        worldConfig
                );

                DIMENSIONS.put(
                        world.getRegistryKey(),
                        worldHandle.getRegistryKey()
                );
                DIMENSIONS.put(
                        worldHandle.getRegistryKey(),
                        world.getRegistryKey()
                );

            } catch (Exception e) {
                BuildingDimension.log("Error creating new dimension : " + e.getMessage());
                return true;
            }
        }
        return false;
    }

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