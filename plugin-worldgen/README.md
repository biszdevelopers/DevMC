# worldgen

Terrain generation and wilderness regeneration for the DevMC server. The managed
world is generated as vanilla terrain shaped into a bounded island; every chunk
that gets farmed or changed is later reset to its barebone state so the map
stays contested. See [docs/design.md](docs/design.md) for the architecture and
[docs/worldgen.md](docs/worldgen.md) for the generation pipeline.

## Features

- **Automatic world generation**: on first start the plugin creates the managed
  world and begins generating chunks. No administrator action is required;
  players are sent to the managed world on join. The world is **vanilla terrain**
  (hills, mountains, oceans, rivers, real caves) shaped into a bounded ~2k × 2k
  island, with warped biome patches that **guarantee every configured biome
  appears at least once**.
- **Change-driven regeneration**: any change to a managed chunk — a player
  break/place, or a non-player cause such as a creeper explosion, fire, or an
  enderman — marks it dirty (a sampled terrain check backstops anything the
  events miss). A chunk is reset once it has been farmed (enough ore/loot
  extracted, or changes left idle) and then goes through the reset pipeline;
  untouched chunks are never reset. Work is bounded per tick by a chunk cap and
  a wall-clock budget.
- **Two generator modes**: the default `IslandWorldGenerator` (vanilla terrain,
  bounded island, guaranteed biome coverage) and `BareboneGenerator` (vanilla
  terrain and biomes, no island). Both disable vanilla ores, plants, structures,
  and mobs so the plugin places its own.
- **Barebone + features**: terrain is generated first, then ores, plants, and
  animals. Ores are vanilla-like in vein size and height distribution but
  rebalanced — more ore overall, biased toward cave-exposed positions so caving
  beats strip mining.
- **Resources**: mining ores and opening containers counts against the chunk's
  resource baseline; heavily farmed chunks cycle back to barebone terrain.
- **POIs**: small, destructible, regenerable points of interest stored as
  schematics in `worldgen/structures.json` (plus a built-in dungeon fallback).
  They are placed during the feature pass, kept within a chunk, and erased and
  re-rolled by regeneration.

Loot caches, POI loot, dungeon chests, and monument loot are currently disabled.

## Requirements

- Java 25
- Paper API 26.2
- Bundler 2.0.0-SNAPSHOT
- WorldEdit 7.4.x (optional; without it ore stripping and POI pasting are
  disabled, but terrain regeneration still works)

## Build

```shell
mvn -f ../plugin-bundler/pom.xml install
mvn package
```

Copy `target/plugin-worldgen-26.2.jar` to the server's `plugins/` directory.

The managed world's generator is registered in `bukkit.yml`:
`worlds: <name>: generator: worldgen`.

## Commands

| Command | Description |
|---|---|
| `/worldadmin reload\|diag\|pregen\|cycle\|regenerate\|clear\|hud\|poi` | World administration and diagnostics |
| `/worldinfo ...` | Console-friendly diagnostics (probe, seed, biome, regen checks, ...) |

Both require the `worldgen.admin` permission (default op).

## Quick start

1. Install Bundler, then drop this jar into `plugins/`.
2. Start the server. The plugin creates the managed world (`setup.world_name`,
   default `devmc`) with the vanilla-terrain island generator, applies
   gamerules, and begins generating chunks automatically.
3. Configure POIs by dropping `.schem` files into
   `ServerData/worldgen/structures/` and listing them in
   `worldgen/structures.json`. If none are defined, the built-in dungeon is used.
4. Verify with `/worldadmin diag`, then use `/worldinfo regenstatus` and the
   regen diagnostics to watch the reset stream.

## Data

Persistent JSON lives under the Bundler storage directory in `worldgen/`:

- `settings.json`, `state.json`, `wilderness.json`
- `structures.json` (POI definitions), `loot.json`
- `snapshots.json`

Locale entries are installed into the ServerData language directory on enable.

## Resetting the managed world

Paper stores the managed world as a **dimension** under the main level, so
deleting `<server>/world/<name>` is not enough. To regenerate from scratch, stop
the server and delete both:

- `<server>/world/dimensions/minecraft/<name>` — the terrain.
- `<server>/ServerData/worldgen/` — setup state, indicators, structure
  definitions, and settings.

Then start the server; setup runs again and regenerates the world.
