package net.fabricmc.BuildingDimension.Persistance;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.BuildingDimension.BuildingDimension;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementCriterion;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

import static net.fabricmc.BuildingDimension.BuildingDimension.isModLoaded;

public class PersistentPlayer {

    private final PersistenceManager manager;

    public PersistentPlayer(@NotNull MinecraftServer server) {
        this.manager = PersistenceManager.getSavedData(server);
    }

    /**
     * Saves the player data to the disk.
     * <p>
     * This is called when the player enters the building dimension.
     * <p>
     * It saves the player's inventory, ender chest, position, gamemode, experience, effects and achievements.
     * <p>
     * It also saves the player's trinkets if the Trinkets mod is loaded.
     *
     * @param player    The player to save
     * @param dimension The dimension the player is in before entering the building dimension
     */
    public void save(ServerPlayerEntity player, RegistryKey<World> dimension) {
        savePlayerInventory(player, dimension);

        if (!dimension.getValue().getNamespace().equals(BuildingDimension.MOD_ID)) {
            savePosition(player);
            saveGamemode(player);
            saveEnderChest(player, dimension);
            saveExperience(player);
            saveEffect(player);
            saveAchievements(player);
            if (isModLoaded("trinkets")) saveTrinkets(player);
        }
    }

    /**
     * Completely cleans the player.
     * <p>
     * This is called when the player leaves the building dimension.
     * <p>
     * It clears the player's inventory, ender chest, experience, effects and achievements.
     * <p>
     * It also clears the player's trinkets if the Trinkets mod is loaded.
     *
     * @param player The player to clean
     */
    public void cleanPlayer(@NotNull ServerPlayerEntity player) {
        player.getInventory().clear();
        player.getEnderChestInventory().clear();
        player.setExperienceLevel(0);
        player.experienceProgress = 0.0f;
        player.getStatusEffects().clear();
        player.changeGameMode(GameMode.SURVIVAL);

        MinecraftServer server = player.getServer();

        if (server == null) {
            BuildingDimension.logError("Failed to completely clean the player : getting the server failed", new Exception(), player.getCommandSource());
            return;
        }

        for (Advancement advancement : server.getAdvancementLoader().getAdvancements()) {
            Map<String, AdvancementCriterion> criteria = advancement.getCriteria();
            for (String criterion : criteria.keySet()) {
                player.getAdvancementTracker().revokeCriterion(advancement, criterion);
            }
        }

        if (isModLoaded("trinkets")) {
            if (TrinketsApi.getTrinketComponent(player).isPresent()) {
                TrinketsApi.getTrinketComponent(player).get().getAllEquipped().forEach(pair -> {
                    SlotReference slot = pair.getLeft();
                    slot.inventory().clear();
                });
            }
        }
    }

    /**
     * Loads the player data from the disk.
     * <p>
     * This is called when the player enters the building dimension.
     * <p>
     * It loads the player's inventory, ender chest, position, gamemode, experience, effects and achievements.
     * <p>
     * It also loads the player's trinkets if the Trinkets mod is loaded.
     *
     * @param player The player to load
     * @param dimension The dimension the player is in before entering the building dimension
     */
    public void load(ServerPlayerEntity player, RegistryKey<World> dimension) {
        loadPlayerInventory(player, dimension);
        loadEnderChest(player, dimension);

        if (!dimension.getValue().getNamespace().equals(BuildingDimension.MOD_ID)) {
            loadExperience(player);
            loadEffects(player);
            loadAchievements(player);
            if (isModLoaded("trinkets")) loadTrinkets(player);
        }
    }

    private void savePlayerInventory(@NotNull ServerPlayerEntity player, RegistryKey<World> dimension) {
        saveInventory(
                player.getUuidAsString(),
                player.getInventory().writeNbt(new NbtList()),
                dimension,
                "player_inventory"
        );
    }

    private void saveEnderChest(@NotNull ServerPlayerEntity player, RegistryKey<World> dimension) {
        saveInventory(
                player.getUuidAsString(),
                player.getEnderChestInventory().toNbtList(),
                dimension,
                "ender_chest"
        );
    }

    private void loadPlayerInventory(ServerPlayerEntity player, RegistryKey<World> dimension) {
        NbtList inv = loadInventory(player, dimension, "player_inventory");
        player.getInventory().readNbt(inv);
    }

    private void loadEnderChest(ServerPlayerEntity player, RegistryKey<World> dimension) {
        NbtList inv = loadInventory(player, dimension, "ender_chest");
        player.getEnderChestInventory().readNbtList(inv);
    }

    private void saveInventory(String uuid, NbtList inventory, RegistryKey<World> dimension, String inventory_name) {
        NbtCompound player_nbt = (NbtCompound) manager.load(uuid);
        if (player_nbt == null) player_nbt = new NbtCompound();

        NbtCompound dimension_nbt = player_nbt.getCompound(dimension.getValue().toString());
        if (dimension_nbt == null) dimension_nbt = new NbtCompound();

        dimension_nbt.put(inventory_name, inventory);
        player_nbt.put(dimension.getValue().toString(), dimension_nbt);
        manager.save(uuid, player_nbt);
    }

    private @NotNull NbtList loadInventory(@NotNull ServerPlayerEntity player, RegistryKey<World> dimension, String inventory_name) {
        NbtCompound player_nbt = (NbtCompound) manager.load(player.getUuidAsString());
        if (player_nbt == null) return new NbtList();

        NbtCompound dimension_nbt = player_nbt.getCompound(dimension.getValue().toString());
        if (dimension_nbt == null) return new NbtList();

        NbtList inventory_nbt = dimension_nbt.getList(inventory_name, 10);
        if (inventory_nbt == null) return new NbtList();

        return inventory_nbt;
    }

    private void saveExperience(@NotNull ServerPlayerEntity player) {
        NbtCompound player_nbt = (NbtCompound) manager.load(player.getUuidAsString());
        if (player_nbt == null) player_nbt = new NbtCompound();

        player_nbt.putInt("experience", player.experienceLevel);
        player_nbt.putFloat("experienceProgress", player.experienceProgress);
        manager.save(player.getUuidAsString(), player_nbt);
    }

    private void loadExperience(@NotNull ServerPlayerEntity player) {
        NbtCompound player_nbt = (NbtCompound) manager.load(player.getUuidAsString());
        if (player_nbt == null) return;

        player.setExperienceLevel(player_nbt.getInt("experience"));
        player.experienceProgress = player_nbt.getFloat("experienceProgress");
    }

    private void saveEffect(@NotNull ServerPlayerEntity player) {
        NbtCompound player_nbt = (NbtCompound) manager.load(player.getUuidAsString());
        if (player_nbt == null) player_nbt = new NbtCompound();

        NbtList effects = new NbtList();
        for (StatusEffectInstance status : player.getStatusEffects()) {
            NbtCompound effect = new NbtCompound();
            effect.putInt("effect", StatusEffect.getRawId(status.getEffectType()));
            effect.putInt("duration", status.getDuration());
            effect.putInt("amplifier", status.getAmplifier());
            effect.putBoolean("ambient", status.isAmbient());
            effect.putBoolean("visible", status.shouldShowParticles());
            effect.putBoolean("showIcon", status.shouldShowIcon());
            effects.add(effect);
        }

        player_nbt.put("effects", effects);
        manager.save(player.getUuidAsString(), player_nbt);
    }

    private void loadEffects(@NotNull ServerPlayerEntity player) {
        NbtCompound player_nbt = (NbtCompound) manager.load(player.getUuidAsString());
        if (player_nbt == null) return;

        NbtList effects = player_nbt.getList("effects", 10);
        if (effects == null) return;

        for (int i = 0; i < effects.size(); i++) {
            NbtCompound effect = effects.getCompound(i);
            StatusEffectInstance status = new StatusEffectInstance(
                    Objects.requireNonNull(StatusEffect.byRawId(effect.getInt("effect"))),
                    effect.getInt("duration"),
                    effect.getInt("amplifier"),
                    effect.getBoolean("ambient"),
                    effect.getBoolean("visible"),
                    effect.getBoolean("showIcon")
            );
            player.addStatusEffect(status);
        }
    }

    private void savePosition(@NotNull ServerPlayerEntity player) {
        NbtCompound player_nbt = (NbtCompound) manager.load(player.getUuidAsString());
        if (player_nbt == null) player_nbt = new NbtCompound();

        NbtCompound position = new NbtCompound();
        position.putDouble("x", player.getX());
        position.putDouble("y", player.getY());
        position.putDouble("z", player.getZ());

        player_nbt.put("position", position);
        manager.save(player.getUuidAsString(), player_nbt);
    }

    public @NotNull Vec3d getPosition(@NotNull ServerPlayerEntity player) {
        NbtCompound player_nbt = (NbtCompound) manager.load(player.getUuidAsString());
        if (player_nbt == null) return new Vec3d(0, 0, 0);

        NbtCompound position = player_nbt.getCompound("position");
        if (position == null) return new Vec3d(0, 0, 0);

        return new Vec3d(
                position.getDouble("x"),
                position.getDouble("y"),
                position.getDouble("z")
        );
    }

    private void saveGamemode(@NotNull ServerPlayerEntity player) {
        NbtCompound player_nbt = (NbtCompound) manager.load(player.getUuidAsString());
        if (player_nbt == null) player_nbt = new NbtCompound();

        player_nbt.putInt("gamemode", player.interactionManager.getGameMode().getId());
        manager.save(player.getUuidAsString(), player_nbt);
    }

    public GameMode getGamemode(@NotNull ServerPlayerEntity player) {
        NbtCompound player_nbt = (NbtCompound) manager.load(player.getUuidAsString());
        if (player_nbt == null) return GameMode.CREATIVE;

        return GameMode.byId(player_nbt.getInt("gamemode"));
    }

    private void saveAchievements(@NotNull ServerPlayerEntity player) {
        NbtCompound player_nbt = (NbtCompound) manager.load(player.getUuidAsString());
        if (player_nbt == null) player_nbt = new NbtCompound();

        MinecraftServer server = player.getServer();

        if (server == null) {
            BuildingDimension.logError("Failed to save achievements: server is null", new Exception(), player.getCommandSource());
            return;
        }

        NbtList achievements = new NbtList();
        for (Advancement advancement : server.getAdvancementLoader().getAdvancements()) {
            Iterable<String> iterable = player.getAdvancementTracker().getProgress(advancement).getObtainedCriteria();

            for (String s : iterable) {
                NbtCompound achievement = new NbtCompound();
                achievement.putString("advancement", advancement.getId().toString());
                achievement.putString("criterion", s);
                achievements.add(achievement);
            }
        }

        player_nbt.put("achievements", achievements);
        manager.save(player.getUuidAsString(), player_nbt);
    }

    private void loadAchievements(@NotNull ServerPlayerEntity player) {
        NbtCompound player_nbt = (NbtCompound) manager.load(player.getUuidAsString());
        if (player_nbt == null) return;

        NbtList achievements = player_nbt.getList("achievements", 10);
        if (achievements == null) return;

        MinecraftServer server = player.getServer();

        if (server == null) {
            BuildingDimension.logError("Failed to load achievements: server is null", new Exception(), player.getCommandSource());
            return;
        }

        GameRules.BooleanRule announce = player.getWorld().getGameRules().get(GameRules.ANNOUNCE_ADVANCEMENTS);
        boolean should_announce = announce.get();
        announce.set(false, server);

        for (int i = 0; i < achievements.size(); i++) {
            NbtCompound achievement = achievements.getCompound(i);
            player.getAdvancementTracker().grantCriterion(
                    server.getAdvancementLoader().get(new Identifier(achievement.getString("advancement"))),
                    achievement.getString("criterion")
            );
        }

        announce.set(should_announce, server);
    }

    private void saveTrinkets(@NotNull ServerPlayerEntity player){
        NbtCompound player_nbt = (NbtCompound) manager.load(player.getUuidAsString());
        if (player_nbt == null) player_nbt = new NbtCompound();

        NbtCompound trinkets = new NbtCompound();
        if (TrinketsApi.getTrinketComponent(player).isPresent()) {
            TrinketsApi.getTrinketComponent(player).get().writeToNbt(trinkets);
        }

        player_nbt.put("trinkets", trinkets);
        manager.save(player.getUuidAsString(), player_nbt);
    }

    private void loadTrinkets(@NotNull ServerPlayerEntity player){
        NbtCompound player_nbt = (NbtCompound) manager.load(player.getUuidAsString());
        if (player_nbt == null) return;

        NbtCompound trinkets = player_nbt.getCompound("trinkets");
        if (trinkets == null) return;

        if (TrinketsApi.getTrinketComponent(player).isPresent()) {
            TrinketsApi.getTrinketComponent(player).get().readFromNbt(trinkets);
        }
    }
}
