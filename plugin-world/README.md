# world

Restructures Minecraft survival around a safe, rentable **settlement** and a
volatile, contested **wilderness**, in the spirit of Rust and the prison/faction
servers that came before it. The settlement is where you live; the wilderness is
where you risk. See [docs/design.md](docs/design.md) for the full design and
[docs/generation-loop.md](docs/generation-loop.md) for the world generation
pipeline.

## Features

- **Automatic world generation**: on first start the plugin creates the managed
  world and begins generating chunks. No administrator action or dialog is
  required; players are sent to the managed world on join. The world is
  **vanilla terrain** (hills, mountains, oceans, rivers, real caves) shaped into
  a bounded ~2k × 2k island, with warped biome patches that **guarantee every
  configured biome appears at least once**.
- **Zones**: policed settlement, unmonitored districts, and wilderness, with an
  action-bar HUD and per-zone rules.
- **Wilderness regeneration**: **change-driven**. Any change to a wilderness
  chunk — a player break/place, or a non-player cause such as a creeper
  explosion, fire, or an enderman — marks it dirty (a sampled terrain check
  backstops anything the events miss). A chunk is scheduled once it has been
  farmed (enough ore/loot extracted, or changes left idle) and resets to its
  **barebone state** about `regen.cycle_seconds` later, with a deterministic
  per-chunk jitter so resets trickle out steadily. A reset waits until the chunk
  is idle, past the grace period, and unpinned. Untouched chunks are never reset.
  Work is bounded per tick by a chunk cap and a wall-clock budget.
- **Barebone + features**: terrain is generated first, then ores, plants, and
  animals. Ores are vanilla-like in vein size and height distribution but
  rebalanced — more ore overall, biased toward cave-exposed positions so caving
  beats strip mining, with fewer fully buried veins.
- **Dirty regeneration**: any change to a wilderness chunk (player or non-player)
  is fully regenerated — barebone terrain plus ores, plants, animals, loot, and
  POIs — once no player is nearby, via a single bulk WorldEdit copy. Heavily
  farmed chunks also cycle on the chunk schedule.
- **Two generator modes**: the default `IslandWorldGenerator` (vanilla terrain,
  bounded island, guaranteed biome coverage) and `BareboneGenerator` (vanilla
  terrain and biomes, no island). Both disable vanilla ores, plants, structures,
  and mobs so the plugin places its own.
- **POIs**: small, destructible, regenerable points of interest stored as
  schematics in `world/structures.json` (plus a built-in dungeon fallback). They
  are placed during the feature pass, kept within a chunk, and erased and
  re-rolled by regeneration.
- **Monuments**: large landmarks are defined **manually by administrators**
  (from a WorldEdit selection or a radius), are persistent, and are exempt from
  regeneration; only their loot refreshes.
- **Plots and rent**: configurable sub-chunk plots, protection, prepaid rent via
  nits, and instant raidable/claimable expiry with no grace period.
- **One-way economy**: the system vendor buys anything except contraband, with a
  daily quota, and never sells.
- **Police**: PvP is disabled in policed zones; assaults are reported and
  contraband is confiscated on entry.

## Requirements

- Java 25
- Paper API 26.2
- Bundler 2.0.0-SNAPSHOT
- Currency 1.0.0-SNAPSHOT
- WorldEdit 7.4.x (optional; without it ore stripping and POI pasting are
  disabled, but terrain regeneration still works)

## Build

```shell
mvn -f ../plugin-bundler/pom.xml install
mvn -f ../plugin-currency/pom.xml install
mvn package
```

Copy `target/plugin-world-26.2.jar` to the server's `plugins/` directory.

## Commands

| Command | Description |
|---|---|
| `/settlement [list\|info\|spawn\|claim\|rent\|release\|plots\|vendor]` | Player settlement and plot actions |
| `/vendor [sell\|all]` | Sell to the system vendor |
| `/worldadmin reload\|diag\|regenerate\|clear\|hud` | World administration and diagnostics |
| `/worldadmin create\|delete\|addchunk\|removechunk\|setspawn\|setrent\|setperiod\|setplotsize` | Settlement management |
| `/worldadmin monument add\|remove\|list` | Manually defined landmarks |
| `/worldadmin poi list\|reload` | Stored POI schematics |

## Quick start

1. Install Bundler and Currency, then drop this jar into `plugins/`.
2. Start the server. The plugin creates the managed world (`setup.world_name`,
   default `devmc`) with the vanilla-terrain island generator, applies
   gamerules, founds the spawn settlement, and begins generating chunks
   automatically.
3. Configure POIs by dropping `.schem` files into `ServerData/world/structures/`
   and listing them in `world/structures.json`. If none are defined, the built-in
   dungeon is used.
4. Define large monuments manually with `/worldadmin monument add <id>` after
   making a WorldEdit selection around the build.
5. Verify with `/worldadmin diag`, then use `/settlement` and `/plot` to play.

## Data

Persistent JSON lives under the Bundler storage directory:

- `world/settlements.json`, `world/plots.json`, `world/monuments.json`
- `world/structures.json` (POI definitions)
- `world/wilderness.json` (per-chunk indicators)
- `world/snapshots.json`, `world/state.json`
- `world/settings.json`, `world/prices.json`, `world/loot.json`

Locale entries are installed into the ServerData language directory on enable.

## Resetting the managed world

Paper stores the managed world as a **dimension** under the main level, so
deleting `<server>/world/<name>` is not enough. To regenerate from scratch, stop
the server and delete both:

- `<server>/world/dimensions/minecraft/<name>` — the terrain.
- `<server>/ServerData/world/` — settlements, plots, monuments, indicators,
  structure definitions, and the setup state.

Then start the server; setup runs again and regenerates the world. The generator
is registered in `bukkit.yml` (`worlds: <name>: generator: world`) so it is used
whenever the world is loaded.
