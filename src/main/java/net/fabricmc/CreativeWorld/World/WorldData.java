package net.fabricmc.CreativeWorld.World;

import net.fabricmc.CreativeWorld.CreativeWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.UUID;

public class WorldData extends PersistentState {

    public HashMap<UUID,
            HashMap<String,
                    Inventory>> inventories = new HashMap<>();
    public HashMap<UUID, NbtCompound> positions = new HashMap<>();

    public HashMap<UUID,
            HashMap<String, EnderChestInventory>> enderChests = new HashMap<>();

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {

        NbtCompound inventoriesNbt = new NbtCompound();

        inventories.forEach((uuid, worldInv) -> {

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
                inventoriesNbt.put(uuid.toString(), inventoryNbt);
            });

        });
        nbt.put("inventories", inventoriesNbt);

        NbtCompound positionsNbt = new NbtCompound();
        positions.forEach((uuid, position) -> {

            positionsNbt.put(uuid.toString(), position);

        });
        nbt.put("positions", positionsNbt);

        NbtCompound enderChestsNbt = new NbtCompound();
        enderChests.forEach((uuid, worldInv) -> {

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
                enderChestsNbt.put(uuid.toString(), inventoryNbt);
            });

        });

        return nbt;
    }

    public static WorldData createFromNbt(NbtCompound nbt) {
        WorldData worldInventories = new WorldData();

        NbtCompound inventoriesNbt = nbt.getCompound("inventories");

        if (inventoriesNbt != null) {

            inventoriesNbt.getKeys().forEach(uuid -> {
                NbtCompound inventoryNbt = inventoriesNbt.getCompound(uuid);
                DefaultedList<ItemStack> items = DefaultedList.ofSize(36, ItemStack.EMPTY);
                Inventories.readNbt(inventoryNbt, items);

                Inventory inventory = new PlayerInventory(null);
                for (int i = 0; i < items.size(); i++) {
                    inventory.setStack(i, items.get(i));
                }

                worldInventories.inventories.put(UUID.fromString(uuid), new HashMap<>());
                worldInventories.inventories.get(UUID.fromString(uuid)).put(World.OVERWORLD.toString(), inventory);
            });
        }

        NbtCompound positionsNbt = nbt.getCompound("positions");

        if (positionsNbt != null) {
            positionsNbt.getKeys().forEach(uuid -> {
                worldInventories.positions.put(UUID.fromString(uuid), positionsNbt.getCompound(uuid));
            });
        }

        NbtCompound enderChestsNbt = nbt.getCompound("enderChests");

        if (enderChestsNbt != null) {

            enderChestsNbt.getKeys().forEach(uuid -> {
                NbtCompound inventoryNbt = enderChestsNbt.getCompound(uuid);
                DefaultedList<ItemStack> items = DefaultedList.ofSize(36, ItemStack.EMPTY);
                Inventories.readNbt(inventoryNbt, items);

                EnderChestInventory inventory = new EnderChestInventory();
                for (int i = 0; i < items.size(); i++) {
                    inventory.setStack(i, items.get(i));
                }

                worldInventories.enderChests.put(UUID.fromString(uuid), new HashMap<>());
                worldInventories.enderChests.get(UUID.fromString(uuid)).put(World.OVERWORLD.toString(), inventory);
            });
        }

        return worldInventories;
    }

    public static WorldData getWorldData(MinecraftServer server) {
        PersistentStateManager persistentStateManager = server.
            getWorld(World.OVERWORLD).getPersistentStateManager();

        WorldData worldInventories = persistentStateManager.getOrCreate(
                WorldData::createFromNbt,
                WorldData::new,
            CreativeWorld.MOD_ID);

        worldInventories.markDirty();

        return worldInventories;
    }

    public void saveInventory(ServerWorld world, ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        String worldKey = world.getRegistryKey().getValue().toString();
        Inventory inventory = new PlayerInventory(null);

        if (!inventories.containsKey(uuid)) {
            inventories.put(uuid, new HashMap<>());
        }

        for (int i = 0; i < player.getInventory().size(); i++) {
            inventory.setStack(i, player.getInventory().getStack(i));
        }

        inventories.get(uuid).put(worldKey, inventory);
    }

    public Inventory loadInventory(ServerWorld world, ServerPlayerEntity player) {
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

    public void savePosition(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        NbtCompound position = new NbtCompound();
        position.putDouble("x", player.getX());
        position.putDouble("y", player.getY());
        position.putDouble("z", player.getZ());
        position.putFloat("yaw", player.getYaw());
        position.putFloat("pitch", player.getPitch());
        positions.put(uuid, position);
    }

    public TeleportTarget loadPosition(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (!positions.containsKey(uuid)) {
            return new TeleportTarget(
                new net.minecraft.util.math.Vec3d(0, 0, 0),
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

    public void saveEnderChest(ServerWorld world, ServerPlayerEntity player){
        UUID uuid = player.getUuid();
        String worldKey = world.getRegistryKey().getValue().toString();
        EnderChestInventory inventory = new EnderChestInventory();

        if (!enderChests.containsKey(uuid)) {
            enderChests.put(uuid, new HashMap<>());
        }

        for (int i = 0; i < player.getEnderChestInventory().size(); i++) {
            inventory.setStack(i, player.getEnderChestInventory().getStack(i));
        }

        enderChests.get(uuid).put(worldKey, inventory);
    }

    public EnderChestInventory loadEnderChest(ServerWorld world, ServerPlayerEntity player){
        UUID uuid = player.getUuid();
        String worldKey = world.getRegistryKey().getValue().toString();

        if (!enderChests.containsKey(uuid)) {
            enderChests.put(uuid, new HashMap<>());
        }

        if (!enderChests.get(uuid).containsKey(worldKey)) {
            enderChests.get(uuid).put(worldKey, new EnderChestInventory());
        }

        return enderChests.get(uuid).get(worldKey);
    }
}
