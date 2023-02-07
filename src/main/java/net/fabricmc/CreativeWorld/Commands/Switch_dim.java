package net.fabricmc.CreativeWorld.Commands;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.CreativeWorld.CreativeWorld;
import net.fabricmc.CreativeWorld.World.WorldData;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Objects;

import static net.fabricmc.CreativeWorld.World.WorldData.getWorldData;

public class Switch_dim {

    private static final RegistryKey<World> OVERWORLD_WORLD_KEY = CreativeWorld.OVERWORLD_WORLD_KEY;
    private static WorldData WORLD_DATA = CreativeWorld.WORLD_DATA;

    private static final Logger LOGGER = CreativeWorld.LOGGER;

    public static int switch_dim(@NotNull CommandContext<ServerCommandSource> context) {
        Identifier current_dim = context.getSource().getWorld().getRegistryKey().getValue();

        Entity entity = context.getSource().getEntity();
        ServerPlayerEntity player = context.getSource().getPlayer();
        MinecraftServer server = context.getSource().getServer();
        ServerWorld world;
        GameMode gameMode;
        TeleportTarget target;

        if (entity == null) {
            context.getSource().sendMessage(Text.literal("You must be a player to use this command"));
            return -1;
        }

        if (player == null) {
            context.getSource().sendMessage(Text.literal("You must be a player to use this command"));
            return -1;
        }

        if (WORLD_DATA == null) {
            WORLD_DATA = getWorldData(server);
            CreativeWorld.WORLD_DATA = WORLD_DATA;
        }

        if ( current_dim == World.OVERWORLD.getValue()) {

            WORLD_DATA.savePosition(player);
            world = context.getSource().getServer().getWorld(OVERWORLD_WORLD_KEY);
            gameMode = GameMode.CREATIVE;

            WORLD_DATA.saveInventory(context.getSource().getWorld(), player);
            WORLD_DATA.saveEnderChest(context.getSource().getWorld(), player);
            target = new TeleportTarget(
                    entity.getPos(),
                    Objects.requireNonNull(entity).getVelocity(),
                    entity.getYaw(),
                    entity.getPitch()
            );

        } else if ( current_dim == OVERWORLD_WORLD_KEY.getValue()) {

            world = context.getSource().getServer().getWorld(World.OVERWORLD);
            gameMode = GameMode.SURVIVAL;

            WORLD_DATA.saveInventory(context.getSource().getWorld(), player);
            WORLD_DATA.saveEnderChest(context.getSource().getWorld(), player);
            target = WORLD_DATA.loadPosition(player);

        } else {
            context.getSource().sendMessage(Text.literal("The dimension you are in is not supported"));
            return -1;
        }

        if (world == null) {
            context.getSource().sendMessage(Text.literal("The dimension you are in is not supported"));
            return -1;
        }

        FabricDimensions.teleport(
                entity,
                world,
                target
        );

        player.changeGameMode(gameMode);
        restoreData(world, player);

        return 1;
    }

    static void restoreData(ServerWorld world, ServerPlayerEntity player){
        Inventory inventory = WORLD_DATA.loadInventory(world, player);
        EnderChestInventory enderChestInventory = WORLD_DATA.loadEnderChest(world, player);
        player.getInventory().clear();
        player.getEnderChestInventory().clear();

        if (inventory == null) {
            return;
        }

        for (int i = 0; i < inventory.size(); i++) {
            player.getInventory().setStack(i, inventory.getStack(i));
        }

        if (enderChestInventory == null) {
            return;
        }

        for (int i = 0; i < enderChestInventory.size(); i++) {
            player.getEnderChestInventory().setStack(i, enderChestInventory.getStack(i));
        }
    }

}
