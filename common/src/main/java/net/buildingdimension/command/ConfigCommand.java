package net.buildingdimension.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.buildingdimension.config.BuildingDimensionConfig;
import net.buildingdimension.config.SwitchWhitelist;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.util.List;

/**
 * {@code /buildingdimension reload} — reloads {@code building_dimension.properties} and
 * {@code building_dimension_whitelist.txt} from disk without a server restart.
 * <p>
 * {@code /buildingdimension whitelist add|remove|list} — manages the /switch whitelist without
 * needing direct file access, for {@link BuildingDimensionConfig#switchRequireWhitelist()}.
 * <p>
 * Operator-only (the same permission level vanilla's own {@code /gamerule} requires).
 */
public final class ConfigCommand {

    private ConfigCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("buildingdimension")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("reload").executes(ConfigCommand::reload))
                .then(Commands.literal("whitelist")
                    .then(Commands.literal("add")
                        .then(Commands.argument("player", StringArgumentType.word())
                            .executes(context -> whitelistAdd(context, StringArgumentType.getString(context, "player")))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("player", StringArgumentType.word())
                            .executes(context -> whitelistRemove(context, StringArgumentType.getString(context, "player")))))
                    .then(Commands.literal("list").executes(ConfigCommand::whitelistList)))
        );
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        BuildingDimensionConfig.load();
        SwitchWhitelist.load();
        context.getSource().sendSystemMessage(Component.translatable("commands.building_dimension.config.reloaded"));
        return 1;
    }

    private static int whitelistAdd(CommandContext<CommandSourceStack> context, String player) {
        CommandSourceStack source = context.getSource();
        if (SwitchWhitelist.add(player)) {
            source.sendSystemMessage(Component.translatable("commands.building_dimension.whitelist.added", player));
        } else {
            source.sendSystemMessage(Component.translatable("commands.building_dimension.whitelist.already_added", player));
        }
        return 1;
    }

    private static int whitelistRemove(CommandContext<CommandSourceStack> context, String player) {
        CommandSourceStack source = context.getSource();
        if (SwitchWhitelist.remove(player)) {
            source.sendSystemMessage(Component.translatable("commands.building_dimension.whitelist.removed", player));
        } else {
            source.sendSystemMessage(Component.translatable("commands.building_dimension.whitelist.not_present", player));
        }
        return 1;
    }

    private static int whitelistList(CommandContext<CommandSourceStack> context) {
        List<String> names = SwitchWhitelist.list();
        Component message = names.isEmpty()
            ? Component.translatable("commands.building_dimension.whitelist.empty")
            : Component.translatable("commands.building_dimension.whitelist.list", String.join(", ", names));
        context.getSource().sendSystemMessage(message);
        return 1;
    }
}
