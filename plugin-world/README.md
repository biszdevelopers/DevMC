# world

Restructures Minecraft survival around a safe, rentable **settlement** and a
volatile, contested **wilderness**, in the spirit of Rust and the prison/faction
servers that came before it. The settlement is where you live; the wilderness is
where you risk. See [docs/design.md](docs/design.md) for the full design and
[docs/generation-loop.md](docs/generation-loop.md) for the world generation
pipeline.

## Features

- **Zones**: policed settlement, unmonitored districts, and wilderness, with an
  action-bar HUD and per-zone rules.
- **Wilderness regeneration**: chunks are tracked individually by a **resource
  indicator** (ores and loot remaining) and a **visibility indicator** (recent
  player attention). Idle, depleted chunks are regenerated terrain-only from the
  world seed to their **barebone state**, then features are re-applied. Chunks
  holding death drops are pinned.
- **Barebone + features**: terrain is generated first, then ores, plants, and
  animals. Ores are vanilla-like in vein size and height distribution but
  rebalanced — more ore overall, biased toward cave-exposed positions so caving
  beats strip mining, with fewer fully buried veins.
- **Two-layer restoration**: basic blocks (stone, dirt, sand, ...) respawn
  quickly after being mined, while ores, plants, animals, and POIs cycle on the
  chunk schedule.
- **Dialog-driven setup**: the first operator to join is walked through
  generating a `devMc` world (seed, size, config) via dialogs.
- **Storage and simulation dimension**: a flat admin world stores structure
  blockstates and stages chunk snapshots so the regeneration pipeline can be
  simulated and watched even on a superflat test server. It is also where the
  structure book teleports admins to edit bounding boxes.
- **Rust-map world generation**: an optional generator builds a large island with
  one biome per sector and no vanilla ores/structures, letting the plugin place
  them itself.
- **Monuments and POIs**: monuments spawn once, spaced out, on a cleared pad, and
  are exempt from regeneration; POIs spawn many times up to a cap and
  despawn/respawn with loot and ore regeneration.
- **Custom structures and a vanilla catalog**: a backbone loads schematics,
  pastes them with rotation, fills their containers from loot tables, and runs
  Rust-like event lifecycles through `StructureEventHook`s. Definitions live in
  `world/structures.json`; schematics go in `world/structures/`. Minecraft's
  original structures are seeded into the catalog by default.
- **Plots and rent**: configurable sub-chunk plots, protection, prepaid rent via
  nits, and instant raidable/claimable expiry with no grace period.
- **One-way economy**: the system vendor buys anything except contraband, with a
  daily quota, and never sells.
- **Police**: PvP is disabled in policed zones; assaults are reported and
  contraband is confiscated on entry. The lethal sentry NPC is a future
  integration behind `PoliceService`.

## Requirements

- Java 25
- Paper API 26.2
- Bundler 2.0.0-SNAPSHOT
- Currency 1.0.0-SNAPSHOT
- WorldEdit 7.4.x (optional; regeneration is disabled without it)

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
| `/worldadmin create\|delete\|addchunk\|removechunk\|setspawn\|setrent\|setperiod\|setplotsize\|monument\|setup\|pregen\|reload` | Admin management |
| `/structure list\|paste\|event\|remove\|instances\|book\|preview\|save\|cancel\|reload` | Admin custom-structure management |
| `/worldtest zone\|chunk\|indicators\|force\|clear\|capture\|simulate\|snapshots\|admin\|world\|structure\|diag` | Diagnostics, snapshots, simulation, and forced regeneration |

## Quick start

1. Install Bundler and Currency, then drop this jar into `plugins/`.
2. Join the server. A dialog walks the first operator through generating the
   `devMc` world (seed, size, and configuration). Edit
   `ServerData/world/settings.json` for defaults.
3. Chunks are generated to the barebone state, then features (ores, plants,
   animals) are added.
4. Open the structure book (or run `/structure book`) to configure monuments and
   POIs and their bounding boxes in the storage/testing dimension.
5. Optionally run `/worldadmin pregen <radius>` to generate chunks ahead of time.
6. Verify with `/worldtest diag`, then use `/settlement` and `/plot` to play.
7. Drop `.schem` files into `ServerData/world/structures/` and define them in
   `world/structures.json` for monuments and POIs.

## Data

Persistent JSON lives under the Bundler storage directory:

- `world/settlements.json`, `world/plots.json`, `world/monuments.json`
- `world/structures.json`, `world/vanilla_structures.json`,
  `world/structure_instances.json`
- `world/wilderness.json` (per-chunk indicators)
- `world/snapshots.json`, `world/state.json`
- `world/settings.json`, `world/prices.json`, `world/loot.json`

Locale entries are installed into the ServerData language directory on enable.
