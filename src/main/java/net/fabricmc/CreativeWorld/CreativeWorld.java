package net.fabricmc.CreativeWorld;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.CreativeWorld.Commands.Switch_dim;
import net.fabricmc.CreativeWorld.Commands.Sync_chunk;
import net.fabricmc.CreativeWorld.World.WorldData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;

public class CreativeWorld implements ModInitializer {

	public static final String MOD_ID = "creative_world";
	public static final Logger LOGGER = LoggerFactory.getLogger("creative_world");

	public static final Identifier OVERWORLD = new Identifier("creative_world", "creative_overworld");

	public static final RegistryKey<DimensionOptions> OVERWORLD_KEY = RegistryKey.of(
			Registry.DIMENSION_KEY,
			OVERWORLD
	);

	public static RegistryKey<World> OVERWORLD_WORLD_KEY = RegistryKey.of(
			Registry.WORLD_KEY,
			OVERWORLD_KEY.getValue()
	);

	public static final RegistryKey<DimensionType> OVERWORLD_TYPE_KEY = RegistryKey.of(
			Registry.DIMENSION_TYPE_KEY,
			OVERWORLD
	);

	public static WorldData WORLD_DATA;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register(this::registerCommands);

		OVERWORLD_WORLD_KEY = RegistryKey.of(
				Registry.WORLD_KEY,
				OVERWORLD
		);
	}

	private void registerCommands(@NotNull CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {

		dispatcher
				.register(literal("creative")
						.executes(Switch_dim::switch_dim)

						.then(literal("sync")
								.executes(Sync_chunk::sync_chunk)
						)
				);
	}
}
