package net.fabricmc.BuildingDimension;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.BuildingDimension.Commands.SwitchDimension;
import net.fabricmc.BuildingDimension.Commands.SyncDimension;
import net.fabricmc.BuildingDimension.Commands.Teleport;
import net.fabricmc.BuildingDimension.Events.PersistenceCreator;
import net.fabricmc.BuildingDimension.Events.dimensionLoading;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;

public class BuildingDimension implements ModInitializer {

	public static final String MOD_ID = "building_dimension";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Configs.load();

		CommandRegistrationCallback.EVENT.register(this::registerCommands);
		registerEvents();
	}

	private void registerCommands(@NotNull CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
		dispatcher
				.register(literal("creative")
						.executes(SwitchDimension::switch_dim)

						.then(literal("sync")
								.requires(source -> source.hasPermissionLevel( Configs.OP_SYNC ? 2 : 0))
								.executes(SyncDimension::sync_chunk_one)
								.then(CommandManager.argument("radius", IntegerArgumentType.integer(1, Configs.MAX_RADIUS))
												.executes(SyncDimension::sync_chunk_radius)
								)
						)
						.then(literal("teleport")
								.then(CommandManager.argument("player", EntityArgumentType.player())
										.executes(Teleport::teleport)
								)
						)
				);
	}

	public static void logError(String s, Exception e, ServerCommandSource source) {
		LOGGER.error(s + e.getMessage());

		StackTraceElement[] stackTrace = e.getStackTrace();
		for (StackTraceElement element : stackTrace) {
			LOGGER.error(element.toString());
		}

		source.sendError(Text.of(s + e.getMessage()));
	}

	public static void log(String message) {
		LOGGER.info(message);
	}

	private void registerEvents() {
		PersistenceCreator.init();
		dimensionLoading.init();
		net.fabricmc.BuildingDimension.Events.SyncDimension.init();
	}
}
