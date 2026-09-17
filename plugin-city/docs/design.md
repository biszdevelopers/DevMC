# City Plugin — Design

Status: approved. The MVP in this document is implemented; deferred items are
listed in section 17.

## 1. Vision and pillars

The city is where you live. The wilderness is where you risk. Everything in the
plugin exists to make that sentence true and to keep players moving between the
two.

Pillars:

1. **The city is safe, social, and expensive.** You rent a plot, store your
   things, and pay to stay. Safety and status cost nits.
2. **The wilderness is a contested extraction arena, not a home.** You cannot
   build there. Terrain fully resets. What you carry out is the only thing that
   matters.
3. **Law is a gradient, not a switch.** Policed city, unmonitored districts and
   the underworld, and lawless wilderness each have their own rules.
4. **A one-way economy.** The system buys anything and sells nothing. All goods
   are found, crafted, traded, or fenced.

### 1.1 Core loop

1. Rent a plot in a city (safety, storage, identity).
2. Rent requires nits; nits come from selling goods.
3. The system vendor buys anything at low base prices (safe, capped). Black
   markets pay more, but only in unmonitored space and only for goods they will
   touch.
4. High-value goods come from **monuments** in the wilderness: persistent,
   contested, hazard-gated, with timed loot.
5. Carrying loot home is the dangerous part. Wilderness, unmonitored districts,
   and the underworld are all player-versus-player.
6. **Contraband** only sells at black markets and is confiscated by police in
   policed space.
7. Wanted players flee into the underworld; the city treasury funds the police
   that chase them.

Every system below should be justifiable as a step in this loop. If it is not,
it does not ship.

## 2. Domain model

Pure model types live in `dev.bisz.city.model` and must not leak Bukkit types
where avoidable, so they are unit-testable.

- `ChunkKey` — `(world, chunkX, chunkZ)` value type. The atomic unit of land.
- `ZoneType` — `POLICED`, `UNMONITORED`, `WILDERNESS`.
- `CityRegion` — an irregular set of `ChunkKey`s plus optional sub-chunk
  carve-outs. A city owns one or more regions.
- `City` — `id`, `name`, `world`, regions, `spawn`, `treasury`, flags
  (`rentPrice`, `rentPeriod`, `plotSize`, `pvpAllowed`, `policeEnabled`).
- `PlotId` — `(cityId, gridX, gridZ, level)`; `level` is reserved for vertical
  apartment units.
- `Plot` — `id`, bounds, `state` (`VACANT`, `RENTED`, `DERELICT`, `RAIDABLE`),
  `owner`, `rentPaidUntil`, `zone` (residential/commercial/industrial).
- `RentContract` — plot, owner, amount, period, paid-through timestamp.
- `Monument` — `id`, `world`, bounds, `tier`, `lootTable`, `respawnTicks`,
  `lastLootedAt`, `hazard`, `keycard`, `awakeWindow`.
- `LootTable` / `LootEntry` — weighted item pools, contraband flags.
- `WantedState` — player, `heat`, `expiresAt`, `crimes`.
- `CrimeType` — `WEAPON_DRAWN`, `ASSAULT`, `THEFT`, `PROPERTY_DAMAGE`,
  `CONTRABAND`.

## 3. Zones

Land resolves to a zone by priority: city region > unmonitored carve-out >
wilderness. Unmonitored space can be **surface districts** (alleys, outskirts)
and a **subterranean underworld** beneath the city. For MVP only surface
unmonitored districts are authored; the underworld is a documented extension.

| Zone | Building | PvP | Police | Vendors |
|---|---|---|---|---|
| Policed | Plot owners + admins only | Disabled (guard) | Responds | System vendor, shops |
| Unmonitored | Plot owners + admins only | Enabled | Absent | Black market |
| Wilderness | Disallowed (reset) | Enabled | Absent | None |

A HUD indicator (action bar) always reports the current zone, and the city
border is rendered so players can never be surprised by a rule change.

## 4. Wilderness

The wilderness supports no permanent building. Any block a player places or
breaks is transient by definition; the terrain is scheduled to fully regenerate.

### 4.1 Region indicators

Chunks are grouped into square **regions** of `region.size` chunks (default 4×4,
so 16 chunks). Indicators are tracked per region, not per chunk, so scheduling
is cheap and stable:

- **Resource indicator** (`0..1`) — the fraction of ore and loot nodes still
  present. It starts at `1` after a regeneration and falls as players mine ore
  blocks and loot containers.
- **Visibility indicator** (`0..1`) — recent player attention. It rises while
  players are present in or editing the region, and decays with a configurable
  half-life (`regen.visibility_half_life_seconds`).

Indicators are persisted in `city/wilderness.json` and updated by
`WildernessListener` and the scheduler's player-presence scan.

### 4.2 Regeneration selection

`RegenerationScheduler` evaluates every tracked region each
`regen.evaluate_interval_seconds`. A region is selected when it is **idle**
(visibility at or below `regen.visibility_threshold`) and either **depleted**
(resource at or below `regen.resource_threshold`) or past the hard
`regen.max-age-seconds` cap. Visibility always gates selection, so an active
region is never reset out from under players.

Selected regions enqueue their chunks. Chunks belonging to a city or intersecting
a monument are skipped. Chunks containing a player or holding unrecovered death
drops are **pinned** and skipped. Execution is budgeted at
`regen.chunks-per-tick`.

### 4.3 Terrain-only generation

Each queued chunk is regenerated through the `ChunkRegenerator` SPI:

- `WorldEditChunkRegenerator` (primary) — regenerates terrain from the world
  seed, then **strips natural ores** in bulk (stone ores → stone, deepslate ores
  → deepslate, nether ores → netherrack) so ore placement is fully controlled.
- `NoopChunkRegenerator` (safe fallback) — logs and skips so a missing
  dependency never corrupts a chunk.

WorldEdit is declared as a soft dependency (`softdepend: [WorldEdit]`).

### 4.4 Resource reseed

After terrain generation, resources are placed at fresh random locations:

- **Ore veins:** `OreReseeder` places weighted, depth-aware veins. Each vein is a
  rough blob sized per ore type (coal 8–17, diamond 2–6, ancient debris 1–3, and
  so on) and only replaces stone, deepslate, or netherrack.
- **Small structures:** `SmallStructures` places destructible structures at
  semi-random locations with probability `small_structure.chance` per chunk.
  Structures are kept within one chunk so the next regeneration cleanly erases
  them. The built-in dungeon is a cobblestone room with a zombie spawner and a
  loot chest; more types can be registered.
- **Loot caches:** `LootReseeder` hides weighted caches from `city/loot.json`.

Large landmarks are **monuments** and are never placed here; they are persistent
and exempt from regeneration.

### 4.5 Death drops versus reset

When a player dies in the wilderness, the chunk holding their drops is pinned
from reset until the drops are recovered or `regen.drop-pin-seconds` elapses.
This prevents a reset from silently deleting a player's gear while they are
running back.

## 5. Monuments

Monuments are the wilderness's content backbone: persistent, fixed landmarks
that players learn, race, and fight over. They are **exempt from terrain
reset**; only their loot refreshes.

A monument is registered in `city/monuments.json` with its bounds, tier, loot
table, respawn timer, hazard, and optional keycard requirement. The roster is
built from **custom structures** loaded and pasted through the structure system
(section 6), not from vanilla world generation. The tier table below is the
intended difficulty ladder:

| Tier | Loot character |
|---|---|
| 1 | Commons, small contraband chance |
| 2 | Mid loot, keycards |
| 3 | Rare loot, heavy contraband |
| 4 | Premium loot, high hazard |

Loot chests respawn on a timer. A monument may periodically **awaken** for a
window, broadcasting its location and boosting loot, to concentrate conflict.
Higher tiers may require a keycard bought in the city.

## 6. Custom structures

Custom builds, not vanilla structures, are the content backbone. The structure
system is a general-purpose backbone: it loads schematics, pastes them, fills
their loot, and runs event lifecycles. Gameplay details are supplied elsewhere
through hooks.

### 6.1 Definitions

`StructureRegistry` loads `city/structures.json`. Each definition declares:

- `id`, `name`, `file` (a schematic in `city/structures/`), and `category`.
- `loot_table` filled into every container in the pasted bounds, and
  `loot_respawn_ticks` for Rust-like periodic refills.
- `rotation_y`, `ignore_air`, and `copy_entities` for pasting.
- `event_type` selecting the hooks to dispatch, and `persistent` for
  never-auto-despawn landmarks.

### 6.2 Load and paste

`StructureBridge` isolates the optional WorldEdit dependency. The WorldEdit
bridge loads `.schem`/`.schematic` clipboards and pastes them at a location with
optional Y rotation, returning the placed bounds. Without WorldEdit a disabled
bridge makes spawning a safe no-op.

### 6.3 Loot

After a paste, `StructureLoot` fills every container inside the placed bounds
from the definition's loot table, tagging contraband exactly like wilderness
caches. Loot can refill on a timer.

### 6.4 Events

`StructureService` tracks placed `StructureInstance`s in
`city/structure_instances.json` and exposes an event lifecycle. External code
registers a `StructureEventHook` per `event_type` and receives:

- `onSpawn` — after paste and loot.
- `onActive` — on the maintenance tick.
- `onLoot` — when a player opens a container in the structure.
- `shouldDespawn` — polled for non-persistent structures.
- `onDespawn` — before blocks are cleared.

The backbone owns pasting, loot, timers, persistence, and despawn; broadcasts,
boss bars, keycards, waves, and rewards live in hooks. `/structure list|paste|
event|remove|instances|reload` drives it for testing.

## 7. Cities

- Cities are defined in-game. `/city create <name>` starts a city at the
  player's current chunk; `/city addchunk` and `/city removechunk` edit the
  region from the chunk the admin stands in. A particle outline previews the
  current region.
- A city has a spawn, a treasury, and per-city economy and protection flags.
- A city's chunk set is irregular; chunks are the boundary unit.
- **Deferred:** player-founded cities with upkeep and dissolution, zoning
  enforcement, and vertical apartment units. The model reserves `level` and
  `zone` so these are additive.

## 8. Plots, protection, and rent

- Plots are a configurable sub-chunk grid inside the city. `plot.size` defaults
  to 16 (a full chunk) and may be smaller, so multiple units fit per chunk.
- Building and container access in policed/unmonitored space require plot
  ownership or admin rights. Wilderness never permits building.
- Rent is charged in nits through Currency. A renter may prepay multiple
  periods.
- On expiry there is **no grace period**: protection drops immediately, the plot
  becomes `RAIDABLE`, and anyone may claim it. Contents remain until looted;
  claiming re-protects the plot under the new owner.
- A mailbox/notice system warns before expiry so the outcome is always
  telegraphable.

## 9. Economy

The system vendor is a **one-way valve: it buys anything, sells nothing.**

- Base prices for common goods are deliberately low; daily sell quotas and
  diminishing returns bound the faucet.
- Monument loot is premium. Contraband is never accepted by the system.
- Sinks are player-facing: rent, taxes, black-market purchases, keycards,
  transport, and city upgrades.
- Black markets sit in unmonitored/underworld space, buy and sell at better
  rates, carry limited stock, and are the only buyers of contraband.
- Prices live in `city/prices.json`, are config-driven, and draw item ids from
  the Items registry where possible.

### 8.1 Faucet and sink table

| Faucet | Sink |
|---|---|
| System vendor purchases | Rent |
| Daily stipend (if retained) | City taxes |
| Monument loot sales | Black-market purchases |
| | Keycards / consumables |
| | Transport fees |
| | City upgrades (deferred) |

## 10. Police and PvP

- PvP is disabled by the guard in policed zones. Unmonitored and wilderness
  zones allow it.
- Crime tiers escalate: drawing a weapon, assault, theft, property damage, and
  contraband possession. Heat accumulates into a `WantedState`.
- Response escalates: warning, wanted status, sentry fire, bounty.
- Contraband is confiscated when a wanted player enters policed space.
- Police are funded by the city treasury, tying safety to the economy.
- `PoliceService` is an SPI. The MVP ships a guard + wanted implementation and
  a stub sentry. The lethal NPC sentry is deferred to the devMC NPC plugin and
  slots in behind the same interface.

## 11. Integration matrix

| Plugin | Integration |
|---|---|
| Bundler | JSON persistence, Locale, DevCommand/CommandRegistery, MenuManager, Profiles, NpcService |
| Currency | `CurrencyManager` nits for rent, vendor, black market, fees |
| Combat | Per-zone death/ghost/corpse rules; death-drop pinning |
| Items / Enchants | Loot tables and price tables reference the item registry |
| SMP | Reconcile passive daily nits with risk-based income (reduce/remove) |

## 12. Persistence

All state is file-backed JSON under the Bundler storage directory, each document
schema-tagged:

- `city/cities.json` — cities, regions, treasuries, flags.
- `city/plots.json` — plots, ownership, rent state.
- `city/monuments.json` — monument registry and loot timers.
- `city/structures.json` — custom structure definitions.
- `city/structure_instances.json` — placed structure instances.
- `city/wilderness.json` — region indicators and pins.
- `city/prices.json` — vendor price table.
- `city/loot.json` — loot tables.

Per-player state that is not plot-scoped (current wanted heat, current city)
uses `Profile` metadata.

## 13. Commands, permissions, menus

- `/city` — list, info, spawn, claim, rent, mail.
- `/plot` — info, claim, unclaim, rent, access.
- `/cityadmin` — create, delete, addchunk, removechunk, setspawn, setrent,
  setplotsize, monument, setup, pregen, reload.
- `/vendor` — open the system vendor.
- `/blackmarket` — open the nearest black market (unmonitored space only).
- `/structure` — list, paste, event, remove, instances, reload custom structures.
- `/citytest` — zone, region, force, forceregion, clear, diag.
- Menus: city browser, plot map, plot management, rent, vendor, wanted board.

Permissions follow the `city.*` / `city.admin` convention, default `op` for
admin nodes.

## 14. Localization

User-visible strings come from bundled `lang/{en_us,zh_cn,zh_tw,ja_jp,ko_kr,es_es}.json`
and are installed into the ServerData language directory on enable. Key
prefixes: `city.*`, `plot.*`, `vendor.*`, `police.*`, `command.city.*`. Reuse
the SMP scoreboard's existing `land.address` and `land.wilderness` keys.

## 15. Tunables

`region.size`, `regen.max-age-seconds`, `regen.chunks-per-tick`,
`regen.drop-pin-seconds`, `regen.evaluate-interval-seconds`, `regen.strip-ores`,
`regen.ore-veins-per-chunk`, `regen.loot-caches-per-chunk`,
`regen.resource-threshold`, `regen.visibility-threshold`,
`regen.visibility-half-life-seconds`, `visibility.player-bump`,
`visibility.edit-bump`, `small-structure.chance`, `plot.size`,
`rent.default-price`, `rent.default-period`, `economy.daily-sell-quota`,
`police.wanted-seconds`.

## 16. World setup and diagnostics

`/cityadmin setup` runs an idempotent initialization against the world implied
by the command location:

1. **World** — adopt `setup.world_name`, or create it with the configured
   environment, type, seed, and structure-generation flag. A blank name uses the
   caller's world.
2. **Gamerules and difficulty** — apply every `setup.gamerule.<name>` value and
   `setup.difficulty`.
3. **Spawn** — set the world spawn to the caller when they are in the target
   world.
4. **Border** — center `setup.border_size` on the spawn when non-zero.
5. **City** — found a city named `setup.city_name` at the spawn when
   `setup.spawn_city` is enabled.
6. **Pre-generation** — start a budgeted chunk pre-generation pass when
   `pregen.radius_chunks` is non-zero.

Every step reports success or failure, and re-running setup is safe.
`/cityadmin pregen <radius|stop>` starts or stops pre-generation manually.

`/citytest` provides diagnostics and forced regeneration: `zone`, `region`
(resource and visibility), `force` (regenerate the current chunk),
`forceregion`, `clear` (drop region indicators), and `diag` (backend, economy,
and content counts).

## 17. MVP, deferred work, and risks

### MVP

Core model, persistence, zones, city selection commands, boundary HUD;
wilderness region indicators, terrain-only regeneration, ore veins, small
structures, death-drop pinning; sub-chunk plots, protection, rent/expiry/claim,
menus; buy-only vendor with quotas and contraband tagging; zone PvP guard,
wanted model, and `PoliceService` stub; custom structure loading, pasting, loot
filling, and event hooks; localization; unit tests.

### Deferred

Authored underworld/sewers; vertical apartments and zoning depth; player-founded
cities with upkeep and dissolution; black-market NPCs and stock; bounties; city
upgrades; concrete structure builds and event hooks; telemetry; seasons/wipes.

### Risks

- **Regeneration cost.** Budgeted queue and dirty-only resets are mandatory; a
  bad config must not stall the server.
- **Inflation.** Buy-only plus quotas mitigates but does not remove it; monitor
  sell volume before tuning prices.
- **PvP farming of pinned chunks.** A player could pin a chunk by dying
  repeatedly; the pin timeout bounds this.
- **WorldEdit absence.** Fallbacks must never corrupt terrain; when in doubt,
  skip and log.
- **Paste and clear cost.** Schematics are pasted synchronously; keep them
  modest and avoid scheduling many at once. Clearing on despawn is bounded by
  the recorded bounds.
- **Untrusted schematics.** The registry confines file paths to
  `city/structures/`; only administrators should write files there.
