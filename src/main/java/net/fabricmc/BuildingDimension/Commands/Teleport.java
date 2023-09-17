package net.fabricmc.BuildingDimension.Commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.BuildingDimension.BuildingDimension;
import net.minecraft.command.EntitySelector;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class Teleport {

    public static int teleport(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();

        ServerPlayerEntity player = source.getPlayer();

        if ( player == null ) {
            source.sendError(Text.literal("You must be a player to use this command"));
            return 0;
        }

        if ( !player.getWorld().getRegistryKey().equals(BuildingDimension.OVERWORLD_WORLD_KEY) ) {
            player.sendMessage(Text.literal("You must be in the creative world to use this command"));
            return 0;
        }

        EntitySelector targetSelector = context.getArgument("player", EntitySelector.class);
        ServerPlayerEntity target = targetSelector.getPlayer(context.getSource());

        if ( target == null || !target.getWorld().getRegistryKey().equals(BuildingDimension.OVERWORLD_WORLD_KEY) ) {
            player.sendMessage(Text.literal("The target player must be in the creative world to use this command"));
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
    }
}
