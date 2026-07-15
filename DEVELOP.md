# Developing Building Dimension

Technical documentation for contributors. For what the mod does and how to configure it, see [README.md](README.md).

## Supported loaders

The project uses the [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template) layout: almost all code lives in the loader-agnostic `common` project, with thin `fabric` and `neoforge` entry points. One codebase, one jar per loader.

| Module | Purpose |
| --- | --- |
| `common` | All mod logic (commands, snapshots, dimension mapping, mixins). Compiled against vanilla (NeoForm), no loader APIs. |
| `fabric` | Fabric entry point + command registration hook. |
| `neoforge` | NeoForge entry point + command registration hook. |

Legacy Forge is not targeted: for Minecraft 26.x the ecosystem has consolidated on NeoForge.

Loader-specific code exists only where the loader ecosystems genuinely differ: `IPlatformHelper` (config directory, mod-loaded checks, dev-environment detection) and `IAccessoryHelper` (Trinkets on Fabric vs. Curios on NeoForge, both reached via reflection with no compile-time dependency — see the javadoc on `FabricAccessoryHelper`/`NeoForgeAccessoryHelper` for why).

## Building

Requires Java 24+ to launch Gradle (the Java 25 toolchain used to compile is downloaded automatically).

```
./gradlew build
```

Jars end up in `fabric/build/libs` and `neoforge/build/libs`. GameTest source (`net.buildingdimension.test.*`) is excluded from both shipped jars (see the `tasks.named('jar')` block in each loader's `build.gradle`) — it's still compiled and available to dev/gametest runs, just not packaged for players.

Run a dev client with `./gradlew :fabric:runClient` or `./gradlew :neoforge:runClient`.

Run the GameTest suite with `./gradlew :fabric:runGameTest` or `./gradlew :neoforge:runGameTestServer`. NeoForge only wires its gametest registries when `Services.PLATFORM.isDevelopmentEnvironment()` is true (see `BuildingDimensionNeoForge`'s constructor) — that's what makes stripping the test package from the production jar safe; Fabric's `fabric-gametest` entrypoint is never invoked outside its own test runner regardless.

## How it works

- Building dimensions are created **at runtime**, not declared in a datapack — the same way vanilla creates every dimension at world load (`MinecraftServer#createLevels`), just later and on demand. `DimensionFactory` clones the source dimension's `DimensionType` and `ChunkGenerator` into a new `ServerLevel` with the same seed, which is what lets *any* dimension — vanilla or modded — get a creative counterpart with zero per-mod configuration. This mirrors the v1 mod's `DimensionAPI` (`old/src/main/java/net/buildingdimension/api/DimensionAPI.java`), ported to the current (unobfuscated) Minecraft internals via two small accessor mixins (`common/src/main/java/net/buildingdimension/mixin/`).
- A source dimension `ns:path` maps to `building_dimension:ns_path`; the reverse mapping is derived by scanning loaded levels, so only the *set of source dimensions that have a counterpart* needs to be persisted (`DimensionRegistry`, a `SavedData`) — used to recreate them after a server restart, since dynamically created levels don't survive one on their own (their chunk data does, on disk, under their own dimension folder).
- Player snapshots are stored via a codec-based `SavedData` attached to the overworld (`SwitchDataStore`), one `source` (survival) and one `building` (creative) snapshot per player. `SwitchCommand` never commits a snapshot to the store until *after* the teleport into the target dimension has actually succeeded — `ServerPlayer#teleportTo` can throw, and committing first would wipe the player with no way back.
- `/switch` access is gated by `SwitchAccess` (operator permission via the new `PermissionSet`/`Permissions` API, and/or `SwitchWhitelist`, a plain text file). A denied player either gets refused outright or, if `switchDeniedPlayersUseSpectator` is on, goes through the exact same snapshot pipeline but ends up in `GameType.SPECTATOR` instead of `CREATIVE`.
- `/sync` copies chunks over several ticks via `ChunkSync`, using `ServerChunkCache#addTicketAndLoadWithRadius` (non-blocking) rather than forcing synchronous chunk generation. Per ready chunk it copies, in order: raw block-state sections, block entities (a full NBT round-trip via `BlockEntity#saveWithFullMetadata`/`BlockEntity#loadStatic`, since raw section copying never touches block entities), non-player entities in the same chunk column (`EntityType#loadEntityRecursive` off a `TagValueOutput`/`TagValueInput` round-trip — covers item frames, armor stands, and the display/marker entities many mods use for "fake blocks"), then relighting. Progress is tracked per-requester so a completion message can be sent once every chunk they queued has been processed.
- Building-dimension weather is forced independently of the rest of the server (`buildingDimensionWeather` config) via `ServerLevelWeatherMixin`, which redirects `ServerLevel#getWeatherData()` to a per-level override held in `BuildingDimensionWeather`. This mixin exists because Minecraft 26.x made weather **fully server-wide** — every level's `getWeatherData()` now delegates to one `MinecraftServer`-owned `WeatherData`, so without this override every dimension always shares identical weather.
- All of this static in-memory state (`ChunkSync`'s job queues, `BuildingDimensionWeather`'s per-level cache, `SwitchCommand`'s cooldown map) is cleared in `BuildingDimensionCommon#onServerStopped`, since it's scoped to one server instance — without that, a singleplayer "leave world, open a different one" would carry stale `ResourceKey`/`ServerLevel` references into the new world.

## Known technical gaps

- **No per-building-dimension day/night cycle.** Minecraft 26.x replaced the old fixed 24000-tick day/night clock with a new per-`DimensionType` "Timeline"/`WorldClock` system (see `DimensionType`'s `timelines`/`defaultClock` fields). Building dimensions already share day/night with their source (`DerivedLevelData` delegates `getGameTime()` to the wrapped overworld data), and safely giving them an independent cycle would need a deeper dive into that new subsystem than this pass covered. The weather override above uses a much simpler, well-understood choke point (`getWeatherData()`) and was safe to ship; time-of-day wasn't.
- **Mob-griefing/spawning game rules are still forced server-wide**, not per-dimension. Vanilla has no single choke point for either check (`mobGriefing` is read ad hoc by Creeper, Enderman, fireballs, etc. — unlike, say, structure generation, which funnels through one `ChunkStatusTasks` method `ChunkStatusTasksMixin` already intercepts). A true per-dimension override would mean patching every individual call site, including ones in other mods, which isn't tractable via mixins.
- **Advancements, statistics, and the recipe book** aren't isolated between a dimension and its building counterpart — see the README.

## Ground-truthing the vanilla API

This project targets Minecraft 26.2, which ships fully unobfuscated (real field/method names, not mojmap-only) and has diverged from 1.21.x in several places (permissions are now a `PermissionSet`/`Permission` model instead of integer levels, entity/block-entity NBT (de)serialization goes through `ValueInput`/`ValueOutput` instead of raw `CompoundTag`, `GameProfile` is a record, weather moved server-wide, etc.). Don't assume 1.21.x-era API names still apply — decompiled sources land in `common/build/moddev/artifacts/vanilla-26.2-1-sources.jar` after the first build; extract and grep the relevant class before writing code against it.
