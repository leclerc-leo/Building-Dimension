# Building Dimension

A Minecraft mod that gives every dimension a **creative twin** where players can freely plan their builds, then come back to survival with nothing gained and nothing lost.

Works with **Fabric** and **NeoForge**, for Minecraft 26.2.

## Commands

- **`/switch`** — teleports you between the dimension you're in and its creative "building" counterpart. Building dimensions share the world seed, so the terrain is identical and you arrive at the same coordinates.
- **`/sync [radius]`** — copies the chunks around you (blocks, chests/signs and other block entities, item frames/armor stands and other entities) from the real dimension into the building one, so you can plan a build around what's actually there instead of just raw terrain. Radius defaults to 3 chunks; non-operators are capped (see `syncMaxRadius` below).
- **`/buildingdimension reload`** — reloads the config and the `/switch` whitelist from disk, no restart needed. Operator-only.
- **`/buildingdimension whitelist add|remove|list <player>`** — manages the `/switch` whitelist. Operator-only.

## What's isolated

Your inventory, ender chest, accessory slots (Trinkets on Fabric, Curios on NeoForge — if installed), XP, effects, health, food, and game mode are all snapshotted and swapped on every switch. Nothing can be smuggled out of (or into) a building dimension. Your creative-side inventory is remembered between visits, so you can leave tools and materials stashed there.

**Known limitation:** advancements, statistics, and the recipe book are *not* isolated — anything you unlock while inside a building dimension stays unlocked in survival too.

## Restricting access to `/switch`

By default anyone can use `/switch`. Three config options (see below) let you lock that down:

- **Operator-only** — require permission level 2 (the same level `/gamerule` requires).
- **Whitelist** — require the player to be on `building_dimension_whitelist.txt` (one name per line, managed with `/buildingdimension whitelist`).
- If **both** are enabled, a player needs to satisfy both — operator *and* whitelisted.
- **Spectator fallback** — instead of turning away a player who fails those checks, let them enter in spectator mode so they can still look around at what's been built, without being able to touch anything.

## Configuration

`config/building_dimension.properties` is created with defaults on first launch. Edit it and run `/buildingdimension reload` to apply changes without restarting.

| Key | Default | Description |
| --- | --- | --- |
| `generateStructuresInBuildingDimensions` | `false` | Whether vanilla/modded structures (villages, strongholds, etc.) generate inside building dimensions. Off by default — building dimensions exist to plan around your own terrain, not to be explored. |
| `disableMobGriefing` | `true` | Forces the `mobGriefing` game rule off on startup, so builds aren't undone by creepers, endermen, etc. Game rules are server-wide in vanilla, so this affects every dimension, not just building ones. |
| `disableMobSpawning` | `true` | Forces the `doMobSpawning` game rule off on startup. Same server-wide caveat as above. |
| `syncMaxRadius` | `8` | The largest `/sync` radius a non-operator may request. Operators are never limited. |
| `switchRequireOp` | `false` | Require operator permission (level 2) to use `/switch`. |
| `switchRequireWhitelist` | `false` | Require the player to be on `building_dimension_whitelist.txt` to use `/switch`. |
| `switchDeniedPlayersUseSpectator` | `false` | If a player fails the checks above, let them in anyway as a spectator instead of refusing outright. |
| `buildingDimensionWeather` | `NORMAL` | Forces a building dimension's weather independently of the rest of the server. One of `NORMAL`, `ALWAYS_CLEAR`, `ALWAYS_RAIN`, `ALWAYS_THUNDER`. |

`building_dimension_whitelist.txt` (created alongside the properties file) holds one player name per line — manage it by hand or with `/buildingdimension whitelist add|remove|list`.

**Known limitation:** there's currently no per-building-dimension day/night override (only weather) — Minecraft 26.x replaced the simple day/night clock with a new per-`DimensionType` "Timeline/WorldClock" system, and safely overriding it needs more research than this pass covered.

## Contributing / building from source

See [DEVELOP.md](DEVELOP.md) for the architecture, build instructions, and internals.
