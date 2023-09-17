package net.fabricmc.BuildingDimension.Persistance;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PersistentPlayer {

    public static void save(ServerPlayerEntity player, RegistryKey<World> dimension) {
        savePlayerInventory(player, dimension);
        saveEnderChest(player, dimension);
        saveExperience(player, dimension);
        saveEffect(player, dimension);
    }

    public static void cleanPlayer(@NotNull ServerPlayerEntity player) {
        player.getInventory().clear();
        player.getEnderChestInventory().clear();
        player.setExperienceLevel(0);
        player.experienceProgress = 0.0f;
        player.getStatusEffects().clear();
        player.changeGameMode(GameMode.SURVIVAL);
    }

    public static void load(ServerPlayerEntity player, RegistryKey<World> dimension) {
        loadPlayerInventory(player, dimension);
        loadEnderChest(player, dimension);
        loadExperience(player, dimension);
        loadEffects(player, dimension);
    }

    private static void savePlayerInventory(@NotNull ServerPlayerEntity player, RegistryKey<World> dimension) {
        saveInventory(
                player.getUuidAsString(),
                player.getInventory().writeNbt(new NbtList()),
                dimension,
                "player_inventory"
        );
    }

    private static void saveEnderChest(@NotNull ServerPlayerEntity player, RegistryKey<World> dimension) {
        saveInventory(
                player.getUuidAsString(),
                player.getEnderChestInventory().toNbtList(),
                dimension,
                "ender_chest"
        );
    }

    private static void loadPlayerInventory(ServerPlayerEntity player, RegistryKey<World> dimension) {
        NbtList inv = loadInventory(player, dimension, "player_inventory");
        player.getInventory().readNbt(inv);
    }

    private static void loadEnderChest(ServerPlayerEntity player, RegistryKey<World> dimension) {
        NbtList inv = loadInventory(player, dimension, "ender_chest");
        player.getEnderChestInventory().readNbtList(inv);
    }

    private static void saveInventory(String uuid, NbtList inventory, RegistryKey<World> dimension, String inventory_name) {
        NbtCompound player_nbt = (NbtCompound) PersistenceManager.load(uuid);
        if (player_nbt == null) player_nbt = new NbtCompound();

        NbtCompound dimension_nbt = player_nbt.getCompound(dimension.getValue().toString());
        if (dimension_nbt == null) dimension_nbt = new NbtCompound();

        dimension_nbt.put(inventory_name, inventory);
        player_nbt.put(dimension.getValue().toString(), dimension_nbt);
        PersistenceManager.save(uuid, player_nbt);
    }

    private static @NotNull NbtList loadInventory(@NotNull ServerPlayerEntity player, RegistryKey<World> dimension, String inventory_name) {
        NbtCompound player_nbt = (NbtCompound) PersistenceManager.load(player.getUuidAsString());
        if (player_nbt == null) return new NbtList();

        NbtCompound dimension_nbt = player_nbt.getCompound(dimension.getValue().toString());
        if (dimension_nbt == null) return new NbtList();

        NbtList inventory_nbt = dimension_nbt.getList(inventory_name, 10);
        if (inventory_nbt == null) return new NbtList();

        return inventory_nbt;
    }

    private static void saveExperience(@NotNull ServerPlayerEntity player, RegistryKey<World> dimension) {
        NbtCompound player_nbt = (NbtCompound) PersistenceManager.load(player.getUuidAsString());
        if (player_nbt == null) player_nbt = new NbtCompound();

        NbtCompound dimension_nbt = player_nbt.getCompound(dimension.getValue().toString());
        if (dimension_nbt == null) dimension_nbt = new NbtCompound();

        dimension_nbt.putInt("experience", player.experienceLevel);
        dimension_nbt.putFloat("experienceProgress", player.experienceProgress);
        player_nbt.put(dimension.getValue().toString(), dimension_nbt);
        PersistenceManager.save(player.getUuidAsString(), player_nbt);
    }

    private static void loadExperience(@NotNull ServerPlayerEntity player, RegistryKey<World> dimension) {
        NbtCompound player_nbt = (NbtCompound) PersistenceManager.load(player.getUuidAsString());
        if (player_nbt == null) return;

        NbtCompound dimension_nbt = player_nbt.getCompound(dimension.getValue().toString());
        if (dimension_nbt == null) return;

        player.setExperienceLevel(dimension_nbt.getInt("experience"));
        player.experienceProgress = dimension_nbt.getFloat("experienceProgress");
    }

    private static void saveEffect(@NotNull ServerPlayerEntity player, RegistryKey<World> dimension) {
        NbtCompound player_nbt = (NbtCompound) PersistenceManager.load(player.getUuidAsString());
        if (player_nbt == null) player_nbt = new NbtCompound();

        NbtCompound dimension_nbt = player_nbt.getCompound(dimension.getValue().toString());
        if (dimension_nbt == null) dimension_nbt = new NbtCompound();

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

        dimension_nbt.put("effects", effects);
        player_nbt.put(dimension.getValue().toString(), dimension_nbt);
        PersistenceManager.save(player.getUuidAsString(), player_nbt);
    }

    private static void loadEffects(@NotNull ServerPlayerEntity player, RegistryKey<World> dimension) {
        NbtCompound player_nbt = (NbtCompound) PersistenceManager.load(player.getUuidAsString());
        if (player_nbt == null) return;

        NbtCompound dimension_nbt = player_nbt.getCompound(dimension.getValue().toString());
        if (dimension_nbt == null) return;

        NbtList effects = dimension_nbt.getList("effects", 10);
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

    public static void savePosition(@NotNull ServerPlayerEntity player, RegistryKey<World> dimension) {
        NbtCompound player_nbt = (NbtCompound) PersistenceManager.load(player.getUuidAsString());
        if (player_nbt == null) player_nbt = new NbtCompound();

        NbtCompound dimension_nbt = player_nbt.getCompound(dimension.getValue().toString());
        if (dimension_nbt == null) dimension_nbt = new NbtCompound();

        NbtCompound position = new NbtCompound();
        position.putDouble("x", player.getX());
        position.putDouble("y", player.getY());
        position.putDouble("z", player.getZ());

        dimension_nbt.put("position", position);
        player_nbt.put(dimension.getValue().toString(), dimension_nbt);
        PersistenceManager.save(player.getUuidAsString(), player_nbt);
    }

    public static @NotNull Vec3d loadPosition(@NotNull ServerPlayerEntity player, RegistryKey<World> dimension) {
        NbtCompound player_nbt = (NbtCompound) PersistenceManager.load(player.getUuidAsString());
        if (player_nbt == null) return new Vec3d(0, 0, 0);

        NbtCompound dimension_nbt = player_nbt.getCompound(dimension.getValue().toString());
        if (dimension_nbt == null) return new Vec3d(0, 0, 0);

        NbtCompound position = dimension_nbt.getCompound("position");
        if (position == null) return new Vec3d(0, 0, 0);

        return new Vec3d(
                position.getDouble("x"),
                position.getDouble("y"),
                position.getDouble("z")
        );
    }
}
