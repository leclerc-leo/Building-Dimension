package net.fabricmc.BuildingDimension.Commands;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.BuildingDimension.BuildingDimension;
import net.minecraft.command.EntitySelector;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class Teleport {

    public static int teleport(CommandContext<ServerCommandSource> context) {
        try {
            ServerCommandSource source = context.getSource();

            ServerPlayerEntity player = source.getPlayer();

            if ( player == null ) {
                source.sendError(Text.literal("You must be a player to use this command"));
                return 0;
            }

            if ( !player.getWorld().getRegistryKey().getValue().getNamespace().equals(BuildingDimension.MOD_ID) ) {
                player.sendMessage(Text.literal("You must be in the creative world to use this command"));
                return 0;
            }

            EntitySelector targetSelector = context.getArgument("player", EntitySelector.class);
            ServerPlayerEntity target = targetSelector.getPlayer(context.getSource());

            if ( target == null
                    || !target.getWorld().getRegistryKey().getValue().getNamespace().equals(BuildingDimension.MOD_ID)
                    || !target.getWorld().getRegistryKey().getValue().getNamespace().equals(player.getWorld().getRegistryKey().getValue().getNamespace())
            ) {
                player.sendMessage(Text.literal("The target player must be in the creative world to use this command and must be in the same dimension as you"));
                return 0;
            }

            player.teleport(
                    target.getServerWorld(),
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    target.getYaw(),
                    target.getPitch()
            );

            return 1;
        } catch (Exception e) {
            BuildingDimension.logError("Failed to execute command: ", e, context.getSource());
            return 0;
        }
    }
}
