# Building Dimension

A Minecraft mod that gives every dimension a **creative twin** where players can freely plan their builds, then come back to survival with nothing gained and nothing lost.

- **`/switch`** — teleports you between the dimension you are in and its creative "building" counterpart. The building dimensions share the world seed, so the terrain is identical and you arrive at the same coordinates.
- **Isolated player state** — your inventory, ender chest, XP, effects, health, food and game mode are snapshotted and swapped on every switch. Nothing can be smuggled out of (or into) the building dimensions. Your creative-side inventory is also remembered between visits.
- **`/switch sync <radius>`** *(planned, port from v1)* — copies chunks from the real dimension into the building one so you can plan around your existing builds.

## Supported loaders

The project uses the [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template) layout: almost all code lives in the loader-agnostic `common` project, with thin `fabric` and `neoforge` entry points. One codebase, one jar per loader.

| Module | Purpose |
| --- | --- |
| `common` | All mod logic (commands, snapshots, dimension mapping). Compiled against vanilla (NeoForm), no loader APIs. |
| `fabric` | Fabric entry point + command registration hook. |
| `neoforge` | NeoForge entry point + command registration hook. |

Legacy Forge is not targeted: for Minecraft 26.x the ecosystem has consolidated on NeoForge.

## Building

Requires Java 24+ to launch Gradle (the Java 25 toolchain used to compile is downloaded automatically).

```
./gradlew build
```

Jars end up in `fabric/build/libs` and `neoforge/build/libs`.

Run a dev client with `./gradlew :fabric:runClient` or `./gradlew :neoforge:runClient`.

## How it works

- Building dimensions are created **at runtime**, not declared in a datapack — the same way vanilla creates every dimension at world load (`MinecraftServer#createLevels`), just later and on demand. `DimensionFactory` clones the source dimension's `DimensionType` and `ChunkGenerator` into a new `ServerLevel` with the same seed, which is what lets *any* dimension — vanilla or modded — get a creative counterpart with zero per-mod configuration. This mirrors the v1 mod's `DimensionAPI` (`old/src/main/java/net/buildingdimension/api/DimensionAPI.java`), ported to the current (unobfuscated) Minecraft internals via two small accessor mixins (`common/src/main/java/net/buildingdimension/mixin/`).
- A source dimension `ns:path` maps to `building_dimension:ns_path`; the reverse mapping is derived by scanning loaded levels, so only the *set of source dimensions that have a counterpart* needs to be persisted (`DimensionRegistry`, a `SavedData`) — used to recreate them after a server restart, since dynamically created levels don't survive one on their own (their chunk data does, on disk, under their own dimension folder).
- Player snapshots are stored via a codec-based `SavedData` attached to the overworld (`SwitchDataStore`), one `source` (survival) and one `building` (creative) snapshot per player.

## Configuration

`config/building_dimension.properties` is created with defaults on first launch:

| Key | Default | Description |
| --- | --- | --- |
| `generateStructuresInBuildingDimensions` | `false` | Whether vanilla/modded structures (villages, strongholds, etc.) are allowed to generate inside building dimensions. Off by default, since building dimensions exist to plan builds around the player's own terrain, not to be explored themselves. |

## Roadmap

- [ ] Block portals / other escape routes out of building dimensions (mixin on dimension travel)
- [ ] Handle death/disconnect inside a building dimension
- [ ] Hooks for inventory-adding mods (Trinkets, Curios) via the platform-service layer
- [ ] Disable mob griefing + mob spawning (set both as disabled by default, add in configs).