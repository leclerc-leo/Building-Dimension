package net.fabricmc.BuildingDimension.World;

import net.fabricmc.BuildingDimension.BuildingDimension;
import net.minecraft.advancement.Advancement;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SavedData extends PersistentState {

    private final Map<UUID,
            Map<String,
                    Inventory>> inventories = new HashMap<>();
    private final Map<UUID, NbtCompound> positions = new HashMap<>();

    private final Map<UUID, EnderChestInventory> enderChests = new HashMap<>();

    private final Map<UUID, Integer> experienceLevels = new HashMap<>();
    private final Map<UUID, Float> experienceProgress = new HashMap<>();

    private final Map<UUID, StatusEffectInstance[]> effects = new HashMap<>();

    private final Map<UUID, HashMap<Identifier, Set<String>>> advancements = new HashMap<>();
    private Map<Identifier, Advancement> advancementsList = null;
    private final Map<UUID, GameMode> gameModes = new HashMap<>();

    @Override
    public NbtCompound writeNbt(@NotNull NbtCompound nbt) {

        NbtCompound inventoriesNbt = new NbtCompound();

        inventories.forEach((uuid, worldInv) ->

            worldInv.forEach((world, inventory) -> {

                DefaultedList<ItemStack> items = DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);
                for (int i = 0; i < inventory.size(); i++) {
                    ItemStack stack = inventory.getStack(i);
                    if (!stack.isEmpty()) {
                        items.set(i, stack);
                    }
                }

                NbtCompound inventoryNbt = new NbtCompound();
                Inventories.writeNbt(inventoryNbt, items);
                inventoriesNbt.put(uuid.toString() + "::" + world, inventoryNbt);
            })

        );
        nbt.put("inventories", inventoriesNbt);

        NbtCompound positionsNbt = new NbtCompound();
        positions.forEach((uuid, position) ->
                positionsNbt.put(uuid.toString(), position)
        );
        nbt.put("positions", positionsNbt);

        NbtCompound enderChestsNbt = new NbtCompound();
        enderChests.forEach((uuid, enderChest) -> {

            DefaultedList<ItemStack> items = DefaultedList.ofSize(enderChest.size(), ItemStack.EMPTY);
            for (int i = 0; i < enderChest.size(); i++) {
                ItemStack stack = enderChest.getStack(i);
                if (!stack.isEmpty()) {
                    items.set(i, stack);
                }
            }

            NbtCompound inventoryNbt = new NbtCompound();
            Inventories.writeNbt(inventoryNbt, items);
            enderChestsNbt.put(uuid.toString(), inventoryNbt);

        });
        nbt.put("enderChests", enderChestsNbt);

        NbtCompound experienceLevelsNbt = new NbtCompound();
        experienceLevels.forEach((uuid, level) ->
                experienceLevelsNbt.putInt(uuid.toString(), level)
        );
        nbt.put("experienceLevels", experienceLevelsNbt);

        NbtCompound experienceProgressNbt = new NbtCompound();
        experienceProgress.forEach((uuid, progress) ->
            experienceProgressNbt.putFloat(uuid.toString(), progress)
        );
        nbt.put("experienceProgress", experienceProgressNbt);

        NbtCompound effectsNbt = new NbtCompound();
        effects.forEach((uuid, effect) -> {

            NbtCompound effectNbt = new NbtCompound();
            for (StatusEffectInstance statusEffectInstance : effect) {
                if (statusEffectInstance != null) {
                    effectNbt.put(statusEffectInstance.getEffectType().toString(), statusEffectInstance.writeNbt(new NbtCompound()));
                }
            }
            effectsNbt.put(uuid.toString(), effectNbt);

        });
        nbt.put("effects", effectsNbt);

        NbtCompound advancementsNbt = new NbtCompound();
        advancements.forEach((uuid, advancements) -> {

            NbtCompound advancementNbt = new NbtCompound();
            advancements.forEach((advancement, criterias) -> {

                NbtCompound criteriaNbt = new NbtCompound();
                criterias.forEach((criteria) ->
                    criteriaNbt.putString(criteria, criteria)
                );
                advancementNbt.put(advancement.toString(), criteriaNbt);

            });
            advancementsNbt.put(uuid.toString(), advancementNbt);

        });
        nbt.put("advancements", advancementsNbt);

        return nbt;
    }

    public static @NotNull SavedData createFromNbt(@NotNull NbtCompound nbt) {
        SavedData data = new SavedData();

        NbtCompound inventoriesNbt = nbt.getCompound("inventories");

        if (inventoriesNbt != null) {

            inventoriesNbt.getKeys().forEach(uuidWorld -> {
                String[] uuidWorldSplit = uuidWorld.split("::");
                UUID uuid = UUID.fromString(uuidWorldSplit[0]);
                String world = uuidWorldSplit[1];

                NbtCompound inventoryNbt = inventoriesNbt.getCompound(uuidWorld);
                DefaultedList<ItemStack> items = DefaultedList.ofSize(36, ItemStack.EMPTY);
                Inventories.readNbt(inventoryNbt, items);

                PlayerInventory inventory = new PlayerInventory(null);
                for (int i = 0; i < items.size(); i++) {
                    inventory.setStack(i, items.get(i));
                }


                if (!data.inventories.containsKey(uuid)) {
                    data.inventories.put(uuid, new HashMap<>());
                }
                data.inventories.get(uuid).put(world, inventory);
            });

        }

        NbtCompound positionsNbt = nbt.getCompound("positions");

        if (positionsNbt != null) {
            positionsNbt.getKeys().forEach(uuid ->
                data.positions.put(UUID.fromString(uuid), positionsNbt.getCompound(uuid))
            );
        }

        NbtCompound enderChestsNbt = nbt.getCompound("enderChests");

        if (enderChestsNbt != null) {

            enderChestsNbt.getKeys().forEach(uuid -> {
                NbtCompound inventoryNbt = enderChestsNbt.getCompound(uuid);
                DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);
                Inventories.readNbt(inventoryNbt, items);

                EnderChestInventory inventory = new EnderChestInventory();
                for (int i = 0; i < items.size(); i++) {
                    inventory.setStack(i, items.get(i));
                }

                data.enderChests.put(UUID.fromString(uuid), inventory);
            });
        }

        NbtCompound experienceLevelsNbt = nbt.getCompound("experienceLevels");

        if (experienceLevelsNbt != null) {
            experienceLevelsNbt.getKeys().forEach(uuid ->
                data.experienceLevels.put(UUID.fromString(uuid), experienceLevelsNbt.getInt(uuid))
            );
        }

        NbtCompound experienceProgressNbt = nbt.getCompound("experienceProgress");

        if (experienceProgressNbt != null) {
            experienceProgressNbt.getKeys().forEach(uuid ->
                data.experienceProgress.put(UUID.fromString(uuid), experienceProgressNbt.getFloat(uuid))
            );
        }

        NbtCompound effectsNbt = nbt.getCompound("effects");

        if (effectsNbt != null) {
            effectsNbt.getKeys().forEach(uuid -> {
                NbtCompound effectNbt = effectsNbt.getCompound(uuid);
                StatusEffectInstance[] effects = new StatusEffectInstance[effectNbt.getKeys().size()];
                effectNbt.getKeys().forEach(effect -> {
                    effectsNbt.getCompound(effect).getInt("Amplifier");
                    effectsNbt.getCompound(effect).getInt("Duration");
                    effectsNbt.getCompound(effect).getBoolean("Ambient");
                    effectsNbt.getCompound(effect).getBoolean("ShowParticle");
                });
                data.effects.put(UUID.fromString(uuid), effects);
            });
        }

        NbtCompound advancementsNbt = nbt.getCompound("advancements");

        if (advancementsNbt != null) {
            advancementsNbt.getKeys().forEach(uuid -> {
                NbtCompound advancementNbt = advancementsNbt.getCompound(uuid);
                HashMap<Identifier, Set<String>> advancements = new HashMap<>();
                advancementNbt.getKeys().forEach(advancement -> {
                    NbtCompound criteriaNbt = advancementNbt.getCompound(advancement);
                    Set<String> criterias = new HashSet<>();
                    criteriaNbt.getKeys().forEach(criteria ->
                        criterias.add(criteriaNbt.getString(criteria))
                    );
                    advancements.put(new Identifier(advancement), criterias);
                });
                data.advancements.put(UUID.fromString(uuid), advancements);
            });
        }

        return data;
    }

    public static @NotNull SavedData getSavedData(@NotNull MinecraftServer server) {
        PersistentStateManager persistentStateManager = Objects.requireNonNull(server.
                getWorld(World.OVERWORLD)).getPersistentStateManager();

        SavedData data = persistentStateManager.getOrCreate(
                SavedData::createFromNbt,
                SavedData::new,
            BuildingDimension.MOD_ID);

        data.markDirty();

        return data;
    }

    public void saveInventory(@NotNull ServerWorld world, @NotNull ServerPlayerEntity player) {
        BuildingDimension.log("Saving inventory for " + player.getName().getString());
        StringBuilder items = new StringBuilder();

        UUID uuid = player.getUuid();
        String worldKey = world.getRegistryKey().getValue().toString();
        Inventory inventory = new PlayerInventory(null);

        if (!inventories.containsKey(uuid)) {
            inventories.put(uuid, new HashMap<>());
        }

        for (int i = 0; i < player.getInventory().size(); i++) {
            inventory.setStack(i, player.getInventory().getStack(i));
            items.append(player.getInventory().getStack(i).toString()).append(",");
        }

        BuildingDimension.log("Inventory items: " + items);
        inventories.get(uuid).put(worldKey, inventory);
    }

    public Inventory loadInventory(@NotNull ServerWorld world, @NotNull ServerPlayerEntity player) {
        BuildingDimension.log("Loading inventory for " + player.getName().getString());

        UUID uuid = player.getUuid();
        String worldKey = world.getRegistryKey().getValue().toString();

        if (!inventories.containsKey(uuid)) {
            inventories.put(uuid, new HashMap<>());
        }

        if (!inventories.get(uuid).containsKey(worldKey)) {
            inventories.get(uuid).put(worldKey, new PlayerInventory(null));
        }

        return inventories.get(uuid).get(worldKey);
    }

    public void savePosition(@NotNull ServerPlayerEntity player) {
        BuildingDimension.log("Saving position for " + player.getName().getString() + " at " + player.getPos().toString());

        UUID uuid = player.getUuid();
        NbtCompound position = new NbtCompound();
        position.putDouble("x", player.getX());
        position.putDouble("y", player.getY());
        position.putDouble("z", player.getZ());
        position.putFloat("yaw", player.getYaw());
        position.putFloat("pitch", player.getPitch());
        positions.put(uuid, position);
    }

    public TeleportTarget loadPosition(@NotNull ServerPlayerEntity player) {
        BuildingDimension.log("Loading position for " + player.getName().getString());

        UUID uuid = player.getUuid();
        if (!positions.containsKey(uuid)) {
            BlockPos spawn = Objects.requireNonNull(player.getServer()).getOverworld().getSpawnPos();
            return new TeleportTarget(
                new net.minecraft.util.math.Vec3d(spawn.getX(), spawn.getY(), spawn.getZ()),
                new net.minecraft.util.math.Vec3d(0, 0, 0),
                0,
                0
            );
        }
        NbtCompound position = positions.get(uuid);
        return new TeleportTarget(
            new net.minecraft.util.math.Vec3d(
                position.getDouble("x"),
                position.getDouble("y"),
                position.getDouble("z")
            ),
            new net.minecraft.util.math.Vec3d(
                position.getFloat("yaw"),
                position.getFloat("pitch"),
                0
            ),
            0,
            0
        );
    }

    public void saveEnderChest(@NotNull ServerPlayerEntity player){
        BuildingDimension.log("Saving ender chest for " + player.getName().getString());
        StringBuilder items = new StringBuilder();

        UUID uuid = player.getUuid();
        EnderChestInventory inventory = new EnderChestInventory();

        for (int i = 0; i < player.getEnderChestInventory().size(); i++) {
            inventory.setStack(i, player.getEnderChestInventory().getStack(i));
            items.append(player.getEnderChestInventory().getStack(i).toString()).append(",");
        }

        BuildingDimension.log("Ender chest items: " + items);
        enderChests.put(uuid, inventory);
    }

    public EnderChestInventory loadEnderChest(@NotNull ServerPlayerEntity player){
        BuildingDimension.log("Loading ender chest for " + player.getName().getString());

        UUID uuid = player.getUuid();
        if (!enderChests.containsKey(uuid)) {
            enderChests.put(uuid, new EnderChestInventory());
        }
        return enderChests.get(uuid);
    }

    public void saveExperience(@NotNull ServerPlayerEntity player){
        BuildingDimension.log("Saving experience for " + player.getName().getString() + " at " + player.experienceLevel + " levels and " + player.experienceProgress + " progress");

        UUID uuid = player.getUuid();
        experienceLevels.put(uuid, player.experienceLevel);
        experienceProgress.put(uuid, player.experienceProgress);
    }

    public void loadExperience(@NotNull ServerPlayerEntity player){
        BuildingDimension.log("Loading experience for " + player.getName().getString() + " at " + experienceLevels.get(player.getUuid()) + " levels and " + experienceProgress.get(player.getUuid()) + " progress");

        UUID uuid = player.getUuid();
        if (!experienceLevels.containsKey(uuid)) {
            experienceLevels.put(uuid, 0);
        }
        if (!experienceProgress.containsKey(uuid)) {
            experienceProgress.put(uuid, 0f);
        }
        player.experienceLevel = experienceLevels.get(uuid);
        player.experienceProgress = experienceProgress.get(uuid);
    }

    public void saveEffects(@NotNull ServerPlayerEntity player){
        BuildingDimension.log("Saving effects for " + player.getName().getString());

        UUID uuid = player.getUuid();
        effects.put(uuid, player.getStatusEffects().toArray(new StatusEffectInstance[0]));
    }

    public void loadEffects(@NotNull ServerPlayerEntity player){
        BuildingDimension.log("Loading effects for " + player.getName().getString());

        UUID uuid = player.getUuid();
        if (!effects.containsKey(uuid)) {
            effects.put(uuid, new StatusEffectInstance[]{});
        }
        player.clearStatusEffects();
        for (StatusEffectInstance effect : effects.get(uuid)) {
            player.addStatusEffect(effect);
        }
    }

    public void saveAdvancements(@NotNull ServerPlayerEntity player){
        BuildingDimension.log("Saving advancements for " + player.getName().getString());

        UUID uuid = player.getUuid();

        if(advancementsList == null){
            advancementsList = new HashMap<>();
            for (Advancement advancement : Objects.requireNonNull(player.getServer()).getAdvancementLoader().getAdvancements()) {
                advancementsList.put(advancement.getId(), advancement);
            }
        }

        if (!advancements.containsKey(uuid)) {
            advancements.put(uuid, new HashMap<>());
        }

        for (Advancement advancement : advancementsList.values()) {
            HashMap<Identifier, Set<String>> obtainedCriteria = advancements.get(uuid);

            Iterable<String> iterable = player.getAdvancementTracker().getProgress(advancement).getObtainedCriteria();

            Set<String> criteria = new HashSet<>();
            for (String s : iterable) {
                criteria.add(s);
            }
            obtainedCriteria.put(advancement.getId(), criteria);

            advancements.put(
                uuid,
                obtainedCriteria
            );
        }
    }

    public void loadAdvancements(@NotNull ServerPlayerEntity player){
        BuildingDimension.log("Loading advancements for " + player.getName().getString());

        UUID uuid = player.getUuid();

        if(advancementsList == null){
            advancementsList = new HashMap<>();
            for (Advancement advancement : Objects.requireNonNull(player.getServer()).getAdvancementLoader().getAdvancements()) {
                advancementsList.put(advancement.getId(), advancement);
            }
        }

        if (!advancements.containsKey(uuid)) {
            advancements.put(uuid, new HashMap<>());
        }

        for (Advancement advancement : advancementsList.values()) {
            HashMap<Identifier, Set<String>> obtainedCriteria = advancements.get(uuid);

            if (!obtainedCriteria.containsKey(advancement.getId())) {
                obtainedCriteria.put(advancement.getId(), new HashSet<>());
            }

            Set<String> criteria = obtainedCriteria.get(advancement.getId());
            Set<String> currentCriteria = new HashSet<>();

            for (String s : player.getAdvancementTracker().getProgress(advancement).getObtainedCriteria()) {
                currentCriteria.add(s);
            }

            for (String s : currentCriteria) {
                if (!criteria.contains(s)) {
                    player.getAdvancementTracker().revokeCriterion(advancement, s);
                }
            }
        }
    }

    public void saveGameMode(@NotNull ServerPlayerEntity player){
        BuildingDimension.log("Loading game mode for " + player.getName().getString());

        gameModes.put(player.getUuid(), player.interactionManager.getGameMode());
    }

    public GameMode loadGameMode(@NotNull ServerPlayerEntity player){
        BuildingDimension.log("Loading game mode for " + player.getName().getString());

        if (!gameModes.containsKey(player.getUuid())) {
            gameModes.put(player.getUuid(), GameMode.SURVIVAL);
        }

        return gameModes.get(player.getUuid());
    }
}
