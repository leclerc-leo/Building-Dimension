package net.buildingdimension.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.buildingdimension.Constants;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * World-level storage for the per-player snapshots taken by /switch.
 * <p>
 * Each player has up to two snapshots: the "source" one (their real survival state, taken when
 * they enter a building dimension) and the "building" one (their creative planning state, taken
 * when they leave, so their planning inventory is still there next visit).
 */
public class SwitchDataStore extends SavedData {

    /**
     * @param source   the player's real state, captured when entering a building dimension
     * @param building the player's creative state, captured when leaving a building dimension
     */
    public record PlayerData(Optional<PlayerSnapshot> source, Optional<PlayerSnapshot> building) {

        public static final PlayerData EMPTY = new PlayerData(Optional.empty(), Optional.empty());

        public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PlayerSnapshot.CODEC.optionalFieldOf("source").forGetter(PlayerData::source),
            PlayerSnapshot.CODEC.optionalFieldOf("building").forGetter(PlayerData::building)
        ).apply(instance, PlayerData::new));
    }

    private static final Codec<SwitchDataStore> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerData.CODEC)
        .xmap(map -> new SwitchDataStore(new HashMap<>(map)), store -> store.players)
        .fieldOf("players").codec();

    public static final SavedDataType<SwitchDataStore> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Constants.MOD_ID, "switch_data"),
        SwitchDataStore::new,
        CODEC,
        null
    );

    private final Map<UUID, PlayerData> players;

    public SwitchDataStore() {
        this(new HashMap<>());
    }

    private SwitchDataStore(Map<UUID, PlayerData> players) {
        this.players = players;
    }

    public static SwitchDataStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public PlayerData of(UUID player) {
        return players.getOrDefault(player, PlayerData.EMPTY);
    }

    public void setSourceSnapshot(UUID player, PlayerSnapshot snapshot) {
        players.put(player, new PlayerData(Optional.of(snapshot), of(player).building()));
        setDirty();
    }

    public void setBuildingSnapshot(UUID player, PlayerSnapshot snapshot) {
        players.put(player, new PlayerData(of(player).source(), Optional.of(snapshot)));
        setDirty();
    }
}
