# World Plugin — Design

Status: approved. The rename to `world` and the refined generation loop are
being implemented; deferred items are listed in section 19. Section 18 specifies
the world generation pipeline.

## 1. Vision and pillars

The settlement is where you live. The wilderness is where you risk. Everything
in the plugin exists to make that sentence true and to keep players moving
between the two.

Pillars:

1. **The settlement is safe, social, and expensive.** You rent a plot, store
   your things, and pay to stay. Safety and status cost nits.
2. **The wilderness is a contested extraction arena, not a home.** You cannot
   build there. Terrain fully resets. What you carry out is the only thing that
   matters.
3. **Law is a gradient, not a switch.** Policed settlement, unmonitored
   districts and the underworld, and lawless wilderness each have their own
   rules.
4. **A one-way economy.** The system buys anything and sells nothing. All goods
   are found, crafted, traded, or fenced.

### 1.1 Core loop

1. Rent a plot in a settlement (safety, storage, identity).
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
7. Wanted players flee into the underworld; the settlement treasury funds the
   police that chase them.

Every system below should be justifiable as a step in this loop. If it is not,
it does not ship.

## 2. Domain model

Pure model types live in `dev.bisz.world.model` and must not leak Bukkit types
where avoidable, so they are unit-testable.

- `ChunkKey` — `(world, chunkX, chunkZ)` value type. The atomic unit of land.
- `ZoneType` — `POLICED`, `UNMONITORED`, `WILDERNESS`.
- `Settlement` — an irregular set of `ChunkKey`s plus optional sub-chunk
  carve-outs. A settlement owns one or more regions, a spawn, a treasury, and
  flags (`rentPrice`, `rentPeriod`, `plotSize`, `pvpAllowed`, `policeEnabled`).
- `PlotId` — `(settlementId, gridX, gridZ, level)`; `level` is reserved for
  vertical apartment units.
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

Land resolves to a zone by priority: settlement region > unmonitored carve-out >
wilderness. Unmonitored space can be **surface districts** (alleys, outskirts)
and a **subterranean underworld** beneath the settlement. For MVP only surface
unmonitored districts are authored; the underworld is a documented extension.

| Zone | Building | PvP | Police | Vendors |
|---|---|---|---|---|
| Policed | Plot owners + admins only | Disabled (guard) | Responds | System vendor, shops |
| Unmonitored | Plot owners + admins only | Enabled | Absent | Black market |
| Wilderness | Disallowed (reset) | Enabled | Absent | None |

A HUD indicator (action bar) always reports the current zone, and the settlement
border is rendered so players can never be surprised by a rule change.

## 4. Wilderness

The wilderness supports no permanent building. Any block a player places or
breaks is transient by definition; the terrain is scheduled to fully regenerate.

### 4.1 Chunk state

Regeneration is tracked **per chunk** in `world/wilderness.json` (schema 3). One
record per chunk holds every index:

- **Resource** (`0..1`) — the fraction of ore and loot nodes still present.
  `extracted_nodes` counts what players have removed since the last reset; the
  fraction is derived from it and the baseline.
- **Due time** — when the chunk may next be reset, or `0` (unscheduled) until the
  chunk is farmed.
- **Presence** — the last time a non-spectator player was in the chunk.
- **Edits** — the last change to the chunk plus a `dirty` flag. Player breaks,
  places, and container use are recorded by `WildernessListener`; non-player
  changes (explosions, fire, and block-moving mobs such as endermen, ravagers,
  and withers) are recorded by the same listener's block events, with an
  unload-time terrain sample as a backstop. Benign natural processes (leaf
  decay, snow melt, grass spread, grazing) are ignored, and changes within a
  short settle window after a reset are ignored so the feature pass cannot
  re-dirty its own output.
- **Pin** — death drops keep the chunk from resetting.

Untouched chunks are never scheduled. The state is updated by
`WildernessListener` and the scheduler's player-presence scan.

### 4.2 Regeneration selection

`RegenerationScheduler` evaluates every tracked chunk each
`regen.evaluate_interval_seconds`. The system is **mining-driven**:

1. **Schedule.** A chunk is scheduled once it has been farmed: at least
   `regen.depletion_nodes` resource nodes extracted, or player edits idle for
   `regen.dirty_inactivity_seconds`. Its due time is
   `now + regen.cycle_seconds + jitter`, where the jitter is deterministic per
   chunk so chunks farmed together do not all reset together.
2. **Reset.** A scheduled chunk resets when it is **due**, **idle** (no
   non-spectator player within `regen.player_radius_chunks`), past
   `regen.grace_seconds` since the last presence/edit, and **unpinned**. On reset
   the schedule and all indexes are cleared.

The baseline node count is recorded from the feature pass (ore blocks, loot
caches, and POIs actually placed), so `resource` reflects real content. Work is
bounded by both `regen.chunks-per-tick` and a wall-clock
`regen.budget-millis-per-tick`, and a chunk that becomes occupied while queued is
re-queued rather than dropped. Every schedule, queue, and reset is logged;
`worldinfo regenstatus` reports tracked / scheduled / due / queued / next.

Selected chunks are enqueued individually. Chunks belonging to a settlement or
intersecting a monument are skipped. Chunks containing a player or holding
unrecovered death drops are **pinned** and skipped. Execution is budgeted at
`regen.chunks-per-tick`.

### 4.3 Barebone generation

Each queued chunk is regenerated to its **barebone state** through the
`ChunkRegenerator` SPI:

- Regeneration copies fresh terrain from a **scratch world** created with the
  same seed and generator, so terrain always matches the world seed. (Paper's
  `regenerateChunk` is stubbed out on 26.1+, WorldEdit's `regenerate` uses the
  wrong seed, and loaded/spawn chunks cannot be unloaded, so none of those work.)
- The copy is a **single bulk WorldEdit operation** (`ForwardExtentCopy` through
  an `EditSession`) rather than two full per-block Bukkit walks, which was the
  dominant regeneration cost. Scratch chunks are saved on unload, so later
  cycles load them from disk instead of regenerating them. Without WorldEdit the
  fallback uses a native `Chunk#getChunkSnapshot` capture and per-block restore.
- `WorldEditChunkRegenerator` (primary) can **strip natural ores** in bulk
  (stone ores → stone, deepslate ores → deepslate, nether ores → netherrack),
  but this only runs when `regen.vanilla_ores` is set: the managed generators
  place no decorations, so the scratch terrain contains no ores to strip.
- `NoopChunkRegenerator` (safe fallback) skips the strip so a missing
  dependency never corrupts a chunk.

WorldEdit is declared as a soft dependency (`softdepend: [WorldEdit]`); without
it regeneration still works, but ore stripping and POI pasting are disabled.

### 4.4 Feature reseed

After the barebone terrain is in place, features are re-applied:

- **Ore veins:** `OreReseeder` uses the vanilla `minecraft:ore` values and shape
  (slim capsule with a sine profile), including each ore's air-exposure discard,
  so distribution matches vanilla. Tweaks: `ore.count-multiplier` (slightly more
  overall) and `ore.exposure-weight` (a mild bias against fully buried veins).
  Veins only replace stone, deepslate, or netherrack.
- **Plants:** biome-appropriate vegetation is regenerated on the surface.
- **Animals:** passive animals are spawned on suitable surface terrain.
- **Loot caches:** `LootReseeder` hides weighted caches from `world/loot.json`.

Large landmarks are **monuments** and are never placed here; they are persistent
and exempt from regeneration. Small destructible POIs are placed by the POI
pass (section 5.2).

### 4.5 Death drops versus reset

When a player dies in the wilderness, the chunk holding their drops is pinned
from reset until the drops are recovered or `regen.drop-pin-seconds` elapses.
This prevents a reset from silently deleting a player's gear while they are
running back.

### 4.6 Dirty regeneration and the resource cycle

Changes are regenerated promptly while long-term farming still cycles on the
chunk schedule:

1. **Dirty regeneration** — when a changed wilderness chunk has no non-spectator
   player within `regen.player_radius_chunks` and `regen.fast_regen_seconds` has
   elapsed since the change, the chunk is **fully regenerated**: the barebone
   terrain is overwritten from the scratch world in one bulk WorldEdit copy, and
   ores, plants, animals, loot, and POIs are re-seeded. This is the same pass as
   the scheduled reset, just triggered by a change instead of the resource
   cycle.
2. **Resource cycle** — a chunk that was farmed (enough nodes extracted) is
   additionally scheduled for a reset about `regen.cycle_seconds` later, with a
   deterministic per-chunk jitter, so heavily farmed areas keep cycling even if
   they are not re-dirtied.

## 5. Monuments and POIs

The wilderness has two kinds of authored content: **monuments**, which are large
landmarks defined manually by administrators, and **POIs**, which are small,
destructible, regenerable points of interest stored as schematics.

### 5.1 Monuments

Monuments are the wilderness's content backbone: persistent, fixed landmarks
that players learn, race, and fight over. They are **exempt from terrain
reset**; only their loot refreshes.

- Monuments are **defined manually by administrators**:
  `/worldadmin monument add <id> [tier] [table]` records the admin's WorldEdit
  selection (or a radius around them when there is no selection).
- A monument is registered in `world/monuments.json` with its bounds, tier,
  loot table, respawn timer, hazard, and optional keycard requirement.
- Loot chests respawn on a timer. Higher tiers may require a keycard bought in
  the settlement.

| Tier | Loot character |
|---|---|
| 1 | Commons, small contraband chance |
| 2 | Mid loot, keycards |
| 3 | Rare loot, heavy contraband |
| 4 | Premium loot, high hazard |

### 5.2 POIs

POIs (points of interest) are the repeatable wilderness content, such as
dungeons. They are the **only** structures the plugin stores.

- A POI may spawn **many times**, up to a global **cap**
  (`structures.poi_cap`).
- POIs are placed during the per-chunk feature pass and are **not** persistent:
  they are erased and re-rolled by regeneration.
- POIs are kept within their chunk so regeneration cleanly erases them.

## 6. POI structures

POIs are small builds stored as schematics in `world/structures.json` and placed
by `PoiService` during the feature pass. A built-in dungeon is used as a fallback
when no POIs are registered. Large structures are **not** stored here; they are
monuments, defined manually (section 5.1).

### 6.1 Definitions

`StructureRegistry` loads `world/structures.json`. Each definition declares:

- `id`, `name`, `file` (a schematic in `world/structures/`), and `category`.
- `loot_table` filled into every container in the pasted bounds, and
  `loot_respawn_ticks` for Rust-like periodic refills.
- `rotation_y`, `ignore_air`, and `copy_entities` for pasting.

### 6.2 Placement

`PoiService` rolls `small_structure.chance` per chunk, respects
`structures.poi_cap`, and pastes a randomly chosen definition at the surface
inside the chunk. Without WorldEdit, or with no registered schematics, it falls
back to the built-in **vanilla-style dungeon** (a mossy cobblestone room with a
spawner, one or two loot chests, and the occasional cobweb). Placement is part
of the feature pass, so POIs are re-rolled whenever the chunk regenerates.

### 6.3 Load and paste

`StructureBridge` isolates the optional WorldEdit dependency. The WorldEdit
bridge loads `.schem`/`.schematic` clipboards and pastes them at a location with
optional Y rotation, returning the placed bounds. Without WorldEdit a disabled
bridge makes placement a safe no-op.

### 6.4 Loot

After a paste, `StructureLoot` fills every container inside the placed bounds
from the definition's loot table, tagging contraband exactly like wilderness
caches.

## 7. Settlements

- Every chunk is **wilderness by default**.
- Settlements are defined by an administrator selecting chunks with
  **WorldEdit**. `/worldadmin create <name>` converts the current WorldEdit
  selection into the settlement's chunk set; a manual current-chunk fallback is
  used when there is no selection.
- `/worldadmin addchunk` and `/worldadmin removechunk` edit the region from the
  chunk the admin stands in. A particle outline previews the current region.
- A settlement has a spawn, a treasury, and per-settlement economy and
  protection flags.
- A settlement's chunk set is irregular; chunks are the boundary unit.
- **Deferred:** player-founded settlements with upkeep and dissolution, zoning
  enforcement, and vertical apartment units. The model reserves `level` and
  `zone` so these are additive.

## 8. Plots, protection, and rent

- Plots are a configurable sub-chunk grid inside the settlement. `plot.size`
  defaults to 16 (a full chunk) and may be smaller, so multiple units fit per
  chunk.
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
  transport, and settlement upgrades.
- Black markets sit in unmonitored/underworld space, buy and sell at better
  rates, carry limited stock, and are the only buyers of contraband.
- Prices live in `world/prices.json`, are config-driven, and draw item ids from
  the Items registry where possible.

### 9.1 Faucet and sink table

| Faucet | Sink |
|---|---|
| System vendor purchases | Rent |
| Daily stipend (if retained) | Settlement taxes |
| Monument loot sales | Black-market purchases |
| | Keycards / consumables |
| | Transport fees |
| | Settlement upgrades (deferred) |

## 10. Police and PvP

- PvP is disabled by the guard in policed zones. Unmonitored and wilderness
  zones allow it.
- Crime tiers escalate: drawing a weapon, assault, theft, property damage, and
  contraband possession. Heat accumulates into a `WantedState`.
- Response escalates: warning, wanted status, sentry fire, bounty.
- Contraband is confiscated when a wanted player enters policed space.
- Police are funded by the settlement treasury, tying safety to the economy.
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
schema-tagged. The plugin's storage root is `world/`:

- `world/settlements.json` — settlements, regions, treasuries, flags.
- `world/plots.json` — plots, ownership, rent state.
- `world/monuments.json` — monument registry and loot timers.
- `world/structures.json` — POI definitions.
- `world/wilderness.json` — per-chunk indicators and pins.
- `world/snapshots.json` — named chunk snapshots.
- `world/state.json` — setup completion and world-generation state.
- `world/prices.json` — vendor price table.
- `world/loot.json` — loot tables.

Per-player state that is not plot-scoped (current wanted heat, current
settlement) uses `Profile` metadata.

## 13. Commands, permissions, menus

- `/settlement` — list, info, spawn, claim, rent, release, plots, vendor.
- `/plot` — info, claim, unclaim, rent, access.
- `/worldadmin` — reload, diag, regenerate [radius], clear, hud; create,
  delete, addchunk, removechunk, setspawn, setrent, setperiod, setplotsize;
  monument add|remove|list; poi list|reload.
- `/vendor` — open the system vendor.
- `/blackmarket` — open the nearest black market (unmonitored space only).
- Menus: settlement browser, plot map, plot management, rent, vendor, wanted
  board.

Permissions follow the `world.*` convention (`world.admin`, `world.use`,
`world.vendor`), default `op` for admin nodes.

## 14. Localization

User-visible strings come from bundled
`lang/{en_us,zh_cn,zh_tw,ja_jp,ko_kr,es_es}.json` and are installed into the
ServerData language directory on enable. Key prefixes: `settlement.*`, `plot.*`,
`vendor.*`, `police.*`, `world.*`. Reuse the SMP scoreboard's existing
`land.address` and `land.wilderness` keys.

## 15. Tunables

`regen.cycle-seconds`, `regen.cycle-jitter`, `regen.grace-seconds`,
`regen.depletion-nodes`, `regen.chunks-per-tick`, `regen.drop-pin-seconds`,
`regen.evaluate-interval-seconds`, `regen.dirty-inactivity-seconds`,
`regen.budget-millis-per-tick`, `regen.player-radius-chunks`,
`regen.strip-ores`, `regen.vanilla-ores`,
`regen.ore-veins-per-chunk`, `regen.loot-caches-per-chunk`,
`regen.fast-regen-enabled`, `regen.fast-regen-seconds`,
`regen.verify-changes`,
`ore.count-multiplier`,
`ore.exposure-weight`, `worldgen.terrain.amplitude`, `worldgen.terrain.caves`,
`worldgen.terrain.cave-scale`, `worldgen.terrain.cave-threshold`,
`features.plants.enabled`, `features.animals.enabled`,
`features.animals-per-chunk`, `small-structure.chance`,
`structures.enabled`, `structures.poi-cap`, `storage.dimension`,
`worldgen.island.enabled`, `worldgen.island.size`,
`worldgen.island.coast-fraction`, `worldgen.island.ocean-margin`,
`worldgen.island.sea-level`, `worldgen.biomes`,
`worldgen.climate.sweep`, `worldgen.climate.scale`, `worldgen.climate.warp`,
`worldgen.climate.octaves`,
`pregen.radius-chunks`, `pregen.chunks-per-tick`,
`debug.verify-regen`, `plot.size`,
`rent.default-price`, `rent.default-period`, `economy.daily-sell-quota`,
`police.wanted-seconds`.

## 16. World setup and diagnostics

Setup runs **automatically when the server starts** — no administrator action or
dialog is required. The plugin creates the managed world
(`setup.world_name`, default `devmc`) with the configured generator and then:

1. **Gamerules and difficulty** — apply every `setup.gamerule.<name>` value and
   `setup.difficulty`.
2. **Spawn** — keep the world spawn.
3. **Border** — center `setup.border_size` on the spawn when non-zero.
4. **Settlement** — found a settlement named `setup.settlement_name` at the
   spawn when `setup.spawn_settlement` is enabled.
5. **Pre-generation** — start generating chunks to the completed barebone state,
   then run the feature pass. The radius comes from `pregen.radius_chunks`, or
   is derived from the island/border when unset (capped at 24 chunks to bound
   startup cost; the rest of the map generates on demand). Chunks are generated
   asynchronously with `pregen.chunks-per-tick` kept in flight, so the heavy
   vanilla terrain work never blocks the server.

On later starts the plugin only re-adopts the managed world (Paper does not
auto-load it) and does not repeat setup. `/worldadmin regenerate [radius]`
regenerates the current chunk or a square of chunks (terrain reset + features),
and `/worldadmin diag` reports backends, counts, and status, while `clear` and
`hud` control the chunk indicators and the indicator HUD.

## 17. Storage, snapshots, and simulations

A dedicated admin dimension (`storage.dimension`, default `world_admin`) is a
flat, structure-free world retained for regeneration simulations that do not
depend on the live world type, which matters on a superflat test server. The
supporting services exist but are not currently exposed by a command.

- `ChunkSnapshot` captures a chunk's blockstates into a compact palette plus
  index array; `SnapshotStore` persists named snapshots in
  `world/snapshots.json`.
- `AdminDimensionService` creates the dimension, clears a staging chunk, and
  restores snapshots.
- `RegenerationSimulator` stages a snapshot in the staging chunk and runs the
  real pipeline (ore stripping, ore veins, POIs, loot) for a number of cycles,
  reporting the block-level effect.

## 18. World generation

See [worldgen.md](worldgen.md) for the authoritative pipeline. In short, by
default (`worldgen.island.enabled: true`) the managed world is created with
`IslandWorldGenerator`: **vanilla terrain shaped into a bounded island**, with a
custom biome provider.

- **Vanilla terrain.** The vanilla noise, surface, and cave stages all run, so
  hills, mountains, oceans, rivers, aquifers, and real caves are intact.
  Decorations, structures, and mobs are disabled; the plugin's feature pass
  places ores, plants, animals, POIs, and monuments.
- **Bounded island.** `worldgen.island.size` is the map diameter (default 2048,
  i.e. ~2k × 2k) and the world border is set to it. The outer
  `worldgen.island.coast_fraction` of the radius is vertically compressed toward
  the sea floor and flooded, leaving `worldgen.island.ocean_margin` blocks of
  ocean inside the border. The core terrain is untouched.
- **Guaranteed biome coverage.** `ClimateBiomeProvider` augments the vanilla
  temperature and humidity with large-scale, domain-warped, perpendicular
  gradients plus fractal noise. The gradients span the island, so the map
  visits the full climate range and **every biome in `worldgen.biomes` appears
  at least once**; because the adjusted climate is noisy, biome borders are
  organic contours rather than straight lines. Oceans and rivers are never
  moved.
- **Terrain-following biomes.** Erosion is left vanilla, so mountain biomes
  (`windswept_hills`, `stony_peaks`) only appear on real high ground; seed
  selection requires the island to have mountains.
- **Curated set.** The default list includes badlands and a mountain biome so
  the gold/emerald ore filters have somewhere to apply.

Setting `worldgen.island.enabled: false` restores `BareboneGenerator`: vanilla
terrain shape and biomes with no features. Regeneration restores the same
terrain either way. Monuments are defined manually by administrators in both
modes.

## 19. MVP, deferred work, and risks

### MVP

Core model, persistence, zones, settlement selection commands, boundary HUD;
per-chunk wilderness indicators, barebone regeneration, ore veins, plants,
animals, small POIs, death-drop pinning; sub-chunk plots, protection,
rent/expiry/claim, menus; buy-only vendor with quotas and contraband tagging;
zone PvP guard, wanted model, and `PoliceService` stub; POI schematic loading,
pasting, and loot filling; manually defined monuments; automatic world setup;
localization; unit tests.

### Deferred

Authored underworld/sewers; vertical apartments and zoning depth; player-founded
settlements with upkeep and dissolution; black-market NPCs and stock; bounties;
settlement upgrades; concrete structure builds and event hooks; telemetry;
seasons/wipes.

### Risks

- **Regeneration cost.** Addressed by a bulk WorldEdit terrain copy, persistent
  scratch chunks, redundant-strip skipping, and a per-tick wall-clock budget.
  The queue remains bounded by `regen.chunks-per-tick`; a bad config still must
  not stall the server.
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
  `world/structures/`; only administrators should write files there.
- **Regeneration seed.** Regeneration copies terrain from a same-seed scratch
  world, so it always matches the world seed. Neither Paper's `regenerateChunk`
  (stubbed out) nor WorldEdit's `regenerate` (wrong seed on Paper 26.1+) may be
  used.

## 20. World map (planned)

A future feature will render a browsable map of the managed world (terrain,
settlements, monuments, and POIs). The data is already available: `ChunkKey`
indexes land, `SettlementManager` holds settlement chunks, `MonumentManager`
holds monument bounds, and `ChunkIndicators` tracks per-chunk resource and
visibility. The plan is to expose a `/worldmap` command and a menu that render a
colour-coded top-down view (and a web/PNG export later). Not implemented yet.
