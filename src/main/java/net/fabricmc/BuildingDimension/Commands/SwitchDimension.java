package net.fabricmc.BuildingDimension.Commands;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.BuildingDimension.BuildingDimension;
import net.fabricmc.BuildingDimension.Configs;
import net.fabricmc.BuildingDimension.World.SavedData;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SwitchDimension {

    private static final RegistryKey<World> OVERWORLD_WORLD_KEY = BuildingDimension.OVERWORLD_WORLD_KEY;
    private static SavedData WORLD_DATA = BuildingDimension.WORLD_DATA;

    private static final Map<UUID, Long> last_switch = new HashMap<>();

    public static int switch_dim(@NotNull CommandContext<ServerCommandSource> context) {
        Identifier current_dim = context.getSource().getWorld().getRegistryKey().getValue();

        ServerPlayerEntity player = context.getSource().getPlayer();
        ServerWorld world;
        GameMode gameMode;
        TeleportTarget target;

        if (player == null) {
            context.getSource().sendMessage(Text.literal("You must be a player to use this command"));
            return -1;
        }

        if (last_switch.containsKey(player.getUuid())) {
            long last_switch_time = last_switch.get(player.getUuid());
            long current_time = new Date().getTime();

            if (current_time - last_switch_time < 5000) {
                context.getSource().sendMessage(Text.literal("You must wait 5 second between dimension switches"));
                return -1;
            }
        } else {
            last_switch.put(player.getUuid(), new Date().getTime());
        }

        if (WORLD_DATA == null) {
            MinecraftServer server = context.getSource().getServer();

            WORLD_DATA = SavedData.getSavedData(server);
            BuildingDimension.WORLD_DATA = WORLD_DATA;
        }

        if ( current_dim == World.OVERWORLD.getValue()) {

            BuildingDimension.log("Player : " + player.getName().getString() + " is switching to creative dimension");

            if (Configs.ONLY_OP && !player.hasPermissionLevel(4) && !Configs.NON_OPS_SPECTATOR) {
                context.getSource().sendMessage(Text.literal("You must be an operator to use this command"));
                return -1;
            }

            world = context.getSource().getServer().getWorld(OVERWORLD_WORLD_KEY);
            gameMode =
                    context.getSource().getPlayer().hasPermissionLevel(4) ?
                            GameMode.CREATIVE :
                            Configs.ONLY_OP && Configs.NON_OPS_SPECTATOR ?
                                    GameMode.SPECTATOR :
                                    GameMode.CREATIVE;

            WORLD_DATA.savePosition(player);
            WORLD_DATA.saveInventory(context.getSource().getWorld(), player);
            WORLD_DATA.saveEnderChest(player);
            WORLD_DATA.saveExperience(player);
            WORLD_DATA.saveEffects(player);
            WORLD_DATA.saveAdvancements(player);
            WORLD_DATA.saveGameMode(player);
            target = new TeleportTarget(
                    player.getPos(),
                    player.getVelocity(),
                    player.getYaw(),
                    player.getPitch()
            );

            cleanPlayer(player);

        } else if ( current_dim == OVERWORLD_WORLD_KEY.getValue()) {

            BuildingDimension.log("Player : " + player.getName().getString() + " is switching to survival dimension");

            world = context.getSource().getServer().getWorld(World.OVERWORLD);
            gameMode = WORLD_DATA.loadGameMode(player);

            WORLD_DATA.saveInventory(context.getSource().getWorld(), player);

            cleanPlayer(player);

            EnderChestInventory enderChestInventory = WORLD_DATA.loadEnderChest(player);
            if (enderChestInventory != null) {
                for (int i = 0; i < enderChestInventory.size(); i++) {
                    player.getEnderChestInventory().setStack(i, enderChestInventory.getStack(i));
                }
            }
            WORLD_DATA.loadExperience(player);
            WORLD_DATA.loadEffects(player);
            target = WORLD_DATA.loadPosition(player);
            WORLD_DATA.loadAdvancements(player);

        } else {
            context.getSource().sendMessage(Text.literal("The dimension you are in is not supported"));
            return -1;
        }

        if (world == null) {
            context.getSource().sendMessage(Text.literal("Error switching dimension : Dimension not found"));
            return -1;
        }

        FabricDimensions.teleport(
                player,
                world,
                target
        );

        player.changeGameMode(gameMode);
        Inventory inventory = WORLD_DATA.loadInventory(world, player);

        if (inventory != null) {
            for (int i = 0; i < inventory.size(); i++) {
                player.getInventory().setStack(i, inventory.getStack(i));
            }
        }

        return 1;
    }

    private static void cleanPlayer(ServerPlayerEntity player) {
        player.getEnderChestInventory().clear();
        player.getStatusEffects().clear();
        player.getInventory().clear();
        player.setExperienceLevel(0);
        player.setExperiencePoints(0);
    }
}
