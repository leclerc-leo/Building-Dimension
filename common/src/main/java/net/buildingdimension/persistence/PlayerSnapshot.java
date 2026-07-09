package net.buildingdimension.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.buildingdimension.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A full copy of everything a player could use to smuggle progress across the creative border:
 * inventory, ender chest, accessory slots (Trinkets/Curios, if installed), experience, effects,
 * health/food and game mode, plus where they were.
 * <p>
 * Captured right before a /switch and restored right after the teleport, so each side of the
 * border keeps its own completely independent player state.
 */
public record PlayerSnapshot(
    ResourceKey<Level> dimension,
    Vec3 position,
    float yRot,
    float xRot,
    GameType gameType,
    List<ItemStack> inventory,
    List<ItemStack> enderChest,
    List<ItemStack> accessories,
    int xpLevel,
    float xpProgress,
    int totalXp,
    float health,
    int foodLevel,
    float saturation,
    List<MobEffectInstance> effects
) {

    private static final Codec<GameType> GAME_TYPE_CODEC = StringRepresentable.fromEnum(GameType::values);

    public static final Codec<PlayerSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(PlayerSnapshot::dimension),
        Vec3.CODEC.fieldOf("position").forGetter(PlayerSnapshot::position),
        Codec.FLOAT.fieldOf("y_rot").forGetter(PlayerSnapshot::yRot),
        Codec.FLOAT.fieldOf("x_rot").forGetter(PlayerSnapshot::xRot),
        GAME_TYPE_CODEC.fieldOf("game_type").forGetter(PlayerSnapshot::gameType),
        ItemStack.OPTIONAL_CODEC.listOf().fieldOf("inventory").forGetter(PlayerSnapshot::inventory),
        ItemStack.OPTIONAL_CODEC.listOf().fieldOf("ender_chest").forGetter(PlayerSnapshot::enderChest),
        ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("accessories", List.of()).forGetter(PlayerSnapshot::accessories),
        Codec.INT.fieldOf("xp_level").forGetter(PlayerSnapshot::xpLevel),
        Codec.FLOAT.fieldOf("xp_progress").forGetter(PlayerSnapshot::xpProgress),
        Codec.INT.fieldOf("total_xp").forGetter(PlayerSnapshot::totalXp),
        Codec.FLOAT.fieldOf("health").forGetter(PlayerSnapshot::health),
        Codec.INT.fieldOf("food_level").forGetter(PlayerSnapshot::foodLevel),
        Codec.FLOAT.fieldOf("saturation").forGetter(PlayerSnapshot::saturation),
        MobEffectInstance.CODEC.listOf().fieldOf("effects").forGetter(PlayerSnapshot::effects)
    ).apply(instance, PlayerSnapshot::new));

    public static PlayerSnapshot capture(ServerPlayer player) {
        return new PlayerSnapshot(
            player.level().dimension(),
            player.position(),
            player.getYRot(),
            player.getXRot(),
            player.gameMode.getGameModeForPlayer(),
            copyContainer(player.getInventory()),
            copyContainer(player.getEnderChestInventory()),
            Services.ACCESSORIES.capture(player),
            player.experienceLevel,
            player.experienceProgress,
            player.totalExperience,
            player.getHealth(),
            player.getFoodData().getFoodLevel(),
            player.getFoodData().getSaturationLevel(),
            player.getActiveEffects().stream().map(MobEffectInstance::new).toList()
        );
    }

    /**
     * Restores everything except position/dimension — teleporting is the caller's job.
     */
    public void restore(ServerPlayer player) {
        restoreContainer(player.getInventory(), inventory);
        restoreContainer(player.getEnderChestInventory(), enderChest);
        Services.ACCESSORIES.restore(player, accessories);

        player.setGameMode(gameType);

        player.experienceLevel = xpLevel;
        player.experienceProgress = xpProgress;
        player.totalExperience = totalXp;

        player.setHealth(health);
        player.getFoodData().setFoodLevel(foodLevel);
        player.getFoodData().setSaturation(saturation);

        player.removeAllEffects();
        for (MobEffectInstance effect : effects) {
            player.addEffect(new MobEffectInstance(effect));
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    /**
     * Wipes everything a snapshot covers, so no state leaks into the dimension being entered.
     */
    public static void clean(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
        Services.ACCESSORIES.clear(player);

        player.experienceLevel = 0;
        player.experienceProgress = 0;
        player.totalExperience = 0;

        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);

        player.removeAllEffects();
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static List<ItemStack> copyContainer(Container container) {
        List<ItemStack> items = new ArrayList<>(container.getContainerSize());
        for (int i = 0; i < container.getContainerSize(); i++) {
            items.add(container.getItem(i).copy());
        }
        return items;
    }

    private static void restoreContainer(Container container, List<ItemStack> items) {
        container.clearContent();
        for (int i = 0; i < Math.min(items.size(), container.getContainerSize()); i++) {
            container.setItem(i, items.get(i).copy());
        }
    }
}
