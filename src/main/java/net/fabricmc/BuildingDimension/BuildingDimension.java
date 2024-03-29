package net.fabricmc.BuildingDimension;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.BuildingDimension.Commands.SwitchDimension;
import net.fabricmc.BuildingDimension.Commands.SyncDimension;
import net.fabricmc.BuildingDimension.Commands.Teleport;
import net.fabricmc.BuildingDimension.Events.dimensionLoading;
import net.fabricmc.BuildingDimension.Persistance.PersistenceManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;

public class BuildingDimension implements ModInitializer {

	public static final String MOD_ID = "building_dimension";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static boolean isModLoaded(String modid) {
		return FabricLoader.getInstance().isModLoaded(modid);
	}

	@Override
	public void onInitialize() {
		Configs.load();

		CommandRegistrationCallback.EVENT.register(this::registerCommands);
		registerEvents();
	}

	private void registerCommands(@NotNull CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
		dispatcher
				.register(literal("building")
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

	/**
	 * Logs an error to the console and sends an error message to the player if the source is not null
	 *
	 * @param s The message to log
	 * @param e The exception to log
	 * @param source The source to send the error message to
	 */
	public static void logError(@NotNull String s, @NotNull Exception e, @Nullable ServerCommandSource source) {
		LOGGER.error(s + e.getMessage());

		StackTraceElement[] stackTrace = e.getStackTrace();
		for (StackTraceElement element : stackTrace) {
			LOGGER.error(element.toString());
		}

		if (source != null) source.sendError(Text.of(s + e.getMessage()));
	}

	/**
	 * Logs a message to the console
	 *
	 * @param message The message to log
	 */
	public static void log(String message) {
		LOGGER.info(message);
	}

	/**
	 * Register the different events
	 */
	private void registerEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(PersistenceManager::getSavedData);
		dimensionLoading.init();
		net.fabricmc.BuildingDimension.Events.SyncDimension.init();
	}
}
