# city

Restructures Minecraft survival around a safe, rentable **city** and a volatile,
contested **wilderness**, in the spirit of Rust and the prison/faction servers
that came before it. The city is where you live; the wilderness is where you
risk. See [docs/design.md](docs/design.md) for the full design.

## Features (MVP)

- **Zones**: policed city, unmonitored districts, and wilderness, with an
  action-bar HUD and per-zone rules.
- **Wilderness regeneration**: chunks are grouped into regions indexed by a
  **resource indicator** (ores and loot remaining) and a **visibility indicator**
  (recent player attention). Idle, depleted regions are regenerated terrain-only
  from the world seed, then ore veins, destructible small structures, and loot
  caches are re-randomized at new locations. Chunks holding death drops are
  pinned.
- **Monuments**: persistent, reset-exempt landmarks with tiered, timed loot.
- **Custom structures**: a backbone that loads schematics, pastes them with
  rotation, fills their containers from loot tables, and runs Rust-like event
  lifecycles through `StructureEventHook`s. Definitions live in
  `city/structures.json`; schematic files go in `city/structures/`.
- **Plots and rent**: configurable sub-chunk plots, protection, prepaid rent via
  nits, and instant raidable/claimable expiry with no grace period.
- **One-way economy**: the system vendor buys anything except contraband, with a
  daily quota, and never sells.
- **Police**: PvP is disabled in policed zones; assaults are reported and
  contraband is confiscated on entry. The lethal sentry NPC is a future
  integration behind `PoliceService`.

## Requirements

- Java 17 or newer
- Spigot API 1.20.1
- Bundler 2.0.0-SNAPSHOT
- Currency 1.0.0-SNAPSHOT
- WorldEdit 7.2.x (optional; regeneration is disabled without it)

## Build

```shell
mvn -f ../plugin-bundler/pom.xml install
mvn -f ../plugin-currency/pom.xml install
mvn package
```

Copy `target/plugin-city-26.2.jar` to the server's `plugins/` directory.

## Commands

| Command | Description |
|---|---|
| `/city [list\|info\|spawn\|claim\|rent\|release\|plots\|vendor]` | Player city and plot actions |
| `/vendor [sell\|all]` | Sell to the system vendor |
| `/cityadmin create\|delete\|addchunk\|removechunk\|setspawn\|setrent\|setperiod\|setplotsize\|monument\|setup\|pregen\|reload` | Admin management |
| `/structure list\|paste\|event\|remove\|instances\|reload` | Admin custom-structure management |
| `/citytest zone\|region\|force\|forceregion\|clear\|diag` | Diagnostics and forced regeneration |

## Quick start

1. Install Bundler and Currency, then drop this jar into `plugins/`.
2. Edit `ServerData/city/settings.json` if needed (`setup.world_name`,
   `setup.border_size`, `setup.gamerule.*`, `worlds`, `pregen.*`).
3. Stand where the city should be and run `/cityadmin setup`. It creates or
   adopts the world, applies gamerules and difficulty, sets the spawn, sizes the
   world border, founds the spawn city, and reports each step.
4. Optionally run `/cityadmin pregen <radius>` to generate chunks ahead of time.
5. Verify with `/citytest diag`, then use `/city` and `/plot` to play.
6. Drop `.schem` files into `ServerData/city/structures/` and define them in
   `city/structures.json` for monuments and events.

## Data

Persistent JSON lives under the Bundler storage directory:

- `city/cities.json`, `city/plots.json`, `city/monuments.json`
- `city/structures.json`, `city/structure_instances.json`
- `city/wilderness.json` (region indicators)
- `city/settings.json`, `city/prices.json`, `city/loot.json`

Locale entries are installed into the ServerData language directory on enable.
