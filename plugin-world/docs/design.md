# World Plugin — Design

Status: approved. The rename to `world` and the refined generation loop are
being implemented; deferred items are listed in section 19. The ordered
generation pipeline itself is specified in
[generation-loop.md](generation-loop.md).

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

### 4.1 Chunk indicators

Regeneration is tracked **per chunk** (the earlier 4×4 region grouping is
removed). Indicators are cheap per-chunk scalars:

- **Resource indicator** (`0..1`) — the fraction of ore and loot nodes still
  present. It starts at `1` after a regeneration and falls as players mine ore
  blocks and loot containers.
- **Visibility indicator** (`0..1`) — recent player attention. It rises while
  players are present in or editing the chunk, and decays with a configurable
  half-life (`regen.visibility_half_life_seconds`).

Indicators are persisted in `world/wilderness.json` and updated by
`WildernessListener` and the scheduler's player-presence scan.

### 4.2 Regeneration selection

`RegenerationScheduler` evaluates every tracked chunk each
`regen.evaluate_interval_seconds`. A chunk is selected when it is **idle**
(visibility at or below `regen.visibility_threshold`) and either **depleted**
(resource at or below `regen.resource_threshold`) or past the hard
`regen.max-age-seconds` cap. Visibility always gates selection, so an active
chunk is never reset out from under players.

Selected chunks are enqueued individually. Chunks belonging to a settlement or
intersecting a monument are skipped. Chunks containing a player or holding
unrecovered death drops are **pinned** and skipped. Execution is budgeted at
`regen.chunks-per-tick`.

### 4.3 Barebone generation

Each queued chunk is regenerated to its **barebone state** through the
`ChunkRegenerator` SPI:

- `WorldEditChunkRegenerator` (primary) — regenerates terrain from the world
  seed, then **strips natural ores** in bulk (stone ores → stone, deepslate ores
  → deepslate, nether ores → netherrack) so ore placement is fully controlled.
- `NoopChunkRegenerator` (safe fallback) — logs and skips so a missing
  dependency never corrupts a chunk.

WorldEdit is declared as a soft dependency (`softdepend: [WorldEdit]`).

### 4.4 Feature reseed

After the barebone terrain is in place, features are re-applied:

- **Ore veins:** `OreReseeder` places weighted, depth-aware veins with
  vanilla-like vein sizes and height distributions, rebalanced so that vein
  mining is nerfed and caving is buffed: overall ore volume is increased and
  placement is biased toward air-exposed positions, while fully buried veins are
  reduced. Veins only replace stone, deepslate, or netherrack.
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

### 4.6 Two-layer regeneration

Mining is restored in two layers so the world feels solid immediately while
resources still cycle over time:

1. **Quick layer** — basic blocks (stone, deepslate, dirt, sand, gravel, ...)
   are restored after `regen.simple_respawn_seconds`, as long as no player is
   within `regen.simple_player_radius` and the spot is still empty. Blocks a
   player placed themselves are remembered and never resurrected.
2. **Slow layer** — barebone regeneration plus ore veins, plants, animals,
   loot, and POIs on the chunk schedule (sections 4.1-4.4).

## 5. Monuments and POIs

Structures are classified as **monuments** or **POIs**. The classification
drives placement, persistence, and regeneration behaviour.

### 5.1 Monuments

Monuments are the wilderness's content backbone: persistent, fixed landmarks
that players learn, race, and fight over. They are **exempt from terrain
reset**; only their loot refreshes.

- Each monument spawns **once**, and monuments are **spaced out** by a
  configurable minimum distance.
- Before pasting, a configurable **pad** is cleared/flattened around the
  footprint so the monument is never covered up and never distorts the terrain
  around it.
- A monument is registered in `world/monuments.json` with its bounds, tier,
  loot table, respawn timer, hazard, and optional keycard requirement.
- The roster is built from **custom structures** loaded and pasted through the
  structure system (section 6), not from vanilla world generation. The tier
  table below is the intended difficulty ladder:

| Tier | Loot character |
|---|---|
| 1 | Commons, small contraband chance |
| 2 | Mid loot, keycards |
| 3 | Rare loot, heavy contraband |
| 4 | Premium loot, high hazard |

Loot chests respawn on a timer. A monument may periodically **awaken** for a
window, broadcasting its location and boosting loot, to concentrate conflict.
Higher tiers may require a keycard bought in the settlement.

### 5.2 POIs

POIs (points of interest) are the repeatable wilderness content, such as
dungeons.

- A POI may spawn **many times**, up to a global **cap**
  (`structures.poi_cap`).
- POIs are placed during the per-chunk feature pass and are **not** persistent:
  they despawn and respawn as part of the loot and ore regeneration cycle.
- POIs are kept within their chunk so regeneration cleanly erases them.

## 6. Custom structures

Custom builds, not vanilla structures, are the content backbone. The structure
system is a general-purpose backbone: it loads schematics, pastes them, fills
their loot, and runs event lifecycles. Gameplay details are supplied elsewhere
through hooks.

### 6.1 Definitions

`StructureRegistry` loads `world/structures.json`. Each definition declares:

- `id`, `name`, `file` (a schematic in `world/structures/`), and `category`.
- `type` — `MONUMENT` or `POI`.
- `biomes` — the biomes the structure belongs in.
- `loot_table` filled into every container in the pasted bounds, and
  `loot_respawn_ticks` for Rust-like periodic refills.
- `rotation_y`, `ignore_air`, and `copy_entities` for pasting.
- `event_type` selecting the hooks to dispatch, and `persistent` for
  never-auto-despawn landmarks.
- Optional explicit `bounds`, edited through the admin book.

### 6.2 Vanilla catalog

Minecraft's original structures are loaded by default as catalog entries (id,
type, biomes, size hints) so the operator can place them through the plugin
even though vanilla structure generation is disabled. They are seeded from
`world/vanilla_structures.json` and appear in the admin book alongside custom
schematics.

### 6.3 Admin structure book

The operator receives a **structure book** (also obtainable by command). Each
entry shows the structure's type, biome, and category. Clicking an entry opens a
**file-backed preview**: the structure is pasted into its own slot in the
**storage and testing dimension** and the operator is teleported there. Every
structure is backed by a schematic file.

The operator edits the preview, then saves. `/structure save` (or the book's
**Save preview** button) captures the edited region — the current WorldEdit
selection when present, otherwise the bounds recorded when the preview opened —
and writes it to the schematic file. Custom structures overwrite their existing
file; a previewed vanilla structure is written to a new file and registered as a
custom definition. `/structure cancel` (or the book's **Cancel preview** button)
discards the preview.

### 6.4 Load and paste

`StructureBridge` isolates the optional WorldEdit dependency. The WorldEdit
bridge loads `.schem`/`.schematic` clipboards and pastes them at a location with
optional Y rotation, returning the placed bounds. Without WorldEdit a disabled
bridge makes spawning a safe no-op.

### 6.5 Loot

After a paste, `StructureLoot` fills every container inside the placed bounds
from the definition's loot table, tagging contraband exactly like wilderness
caches. Loot can refill on a timer.

### 6.6 Events

`StructureService` tracks placed `StructureInstance`s in
`world/structure_instances.json` and exposes an event lifecycle. External code
registers a `StructureEventHook` per `event_type` and receives:

- `onSpawn` — after paste and loot.
- `onActive` — on the maintenance tick.
- `onLoot` — when a player opens a container in the structure.
- `shouldDespawn` — polled for non-persistent structures.
- `onDespawn` — before blocks are cleared.

The backbone owns pasting, loot, timers, persistence, and despawn; broadcasts,
boss bars, keycards, waves, and rewards live in hooks. `/structure
list|paste|event|remove|instances|reload` drives it for testing.

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
- `world/structures.json` — custom structure definitions.
- `world/vanilla_structures.json` — vanilla structure catalog entries.
- `world/structure_instances.json` — placed structure instances.
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
- `/worldadmin` — create (WorldEdit selection), delete, addchunk, removechunk,
  setspawn, setrent, setperiod, setplotsize, monument, setup, pregen, reload.
- `/vendor` — open the system vendor.
- `/blackmarket` — open the nearest black market (unmonitored space only).
- `/structure` — list, paste, event, remove, instances, book, stage, bounds,
  reload.
- `/worldtest` — zone, chunk, indicators, force, clear, capture, simulate,
  snapshots, admin, world, structure, diag.
- Menus: settlement browser, plot map, plot management, rent, vendor, wanted
  board, structure book.

Permissions follow the `world.*` convention (`world.admin`, `world.use`,
`world.vendor`), default `op` for admin nodes.

## 14. Localization

User-visible strings come from bundled
`lang/{en_us,zh_cn,zh_tw,ja_jp,ko_kr,es_es}.json` and are installed into the
ServerData language directory on enable. Key prefixes: `settlement.*`, `plot.*`,
`vendor.*`, `police.*`, `world.*`. Reuse the SMP scoreboard's existing
`land.address` and `land.wilderness` keys.

## 15. Tunables

`regen.max-age-seconds`, `regen.chunks-per-tick`, `regen.drop-pin-seconds`,
`regen.evaluate-interval-seconds`, `regen.strip-ores`, `regen.vanilla-ores`,
`regen.ore-veins-per-chunk`, `regen.loot-caches-per-chunk`,
`regen.resource-threshold`, `regen.visibility-threshold`,
`regen.visibility-half-life-seconds`, `regen.simple-respawn-enabled`,
`regen.simple-respawn-seconds`, `regen.simple-player-radius`,
`visibility.player-bump`, `visibility.edit-bump`, `ore.exposure-weight`,
`features.plants.enabled`, `features.animals.enabled`,
`features.animals-per-chunk`, `small-structure.chance`,
`structures.enabled`, `structures.clear-on-despawn`,
`structures.monument-spacing-chunks`, `structures.monument-pad-radius`,
`structures.poi-cap`, `storage.dimension`, `worldgen.rustmap.enabled`,
`worldgen.rustmap.island-radius`, `worldgen.rustmap.sea-level`,
`worldgen.rustmap.biomes`, `worldgen.rustmap.structure-radius`, `plot.size`,
`rent.default-price`, `rent.default-period`, `economy.daily-sell-quota`,
`police.wanted-seconds`.

## 16. World setup and diagnostics

Setup is dialog-driven (section 1 of
[generation-loop.md](generation-loop.md)). `/worldadmin setup` remains as the
idempotent command equivalent and runs against the world implied by the command
location:

1. **World** — adopt `setup.world_name`, or create it with the configured
   environment, type, seed, and structure-generation flag. A blank name uses the
   caller's world.
2. **Gamerules and difficulty** — apply every `setup.gamerule.<name>` value and
   `setup.difficulty`.
3. **Spawn** — set the world spawn to the caller when they are in the target
   world.
4. **Border** — center `setup.border_size` on the spawn when non-zero.
5. **Settlement** — found a settlement named `setup.settlement_name` at the
   spawn when `setup.spawn_settlement` is enabled.
6. **Pre-generation** — generate chunks to the completed barebone state, then
   run the feature pass, when `pregen.radius_chunks` is non-zero.

Every step reports success or failure, and re-running setup is safe.
`/worldadmin pregen <radius|stop>` starts or stops pre-generation manually.

`/worldtest` provides diagnostics and forced regeneration: `zone`, `chunk`
(resource and visibility), `force` (regenerate the current chunk), `clear`
(drop chunk indicators), and `diag` (backend, economy, and content counts).

## 17. Storage, snapshots, and simulations

A dedicated admin dimension (`storage.dimension`, default `world_admin`) is a
flat, structure-free world used to store and inspect structure blockstates and
to run regeneration simulations that do not depend on the live world type, which
matters on a superflat test server. It is also the dimension the admin structure
book teleports to for bounds editing.

- `ChunkSnapshot` captures a chunk's blockstates into a compact palette plus
  index array; `SnapshotStore` persists named snapshots in
  `world/snapshots.json`.
- `AdminDimensionService` creates the dimension, clears a staging chunk, restores
  snapshots, and places vanilla structures with `/place structure`.
- `RegenerationSimulator` stages a snapshot in the staging chunk and runs the
  real pipeline (ore stripping, ore veins, small structures, loot) for a number
  of cycles, reporting the block-level effect. Synthetic terrain can be generated
  so simulations work even on superflat.
- `/worldtest` drives it: `chunk`, `capture`, `simulate`, `snapshots`, `admin`,
  and `structure`.

## 18. Rust-map world generation

When `worldgen.rustmap.enabled` is true, setup creates worlds with
`RustMapGenerator` instead of vanilla generation:

- A large radial island falling off to ocean at `island_radius`.
- One biome per angular sector (`rustmap.biomes`), with ocean outside.
- Terrain only: ores, decorations, and structures are disabled so the plugin's
  own feature pass and structure system place them.
- `RustMapPlanter` places one copy of each monument at evenly spaced anchors
  around the island (`rustmap.structure_radius`).

## 19. MVP, deferred work, and risks

### MVP

Core model, persistence, zones, settlement selection commands, boundary HUD;
per-chunk wilderness indicators, barebone regeneration, ore veins, plants,
animals, small POIs, death-drop pinning; sub-chunk plots, protection,
rent/expiry/claim, menus; buy-only vendor with quotas and contraband tagging;
zone PvP guard, wanted model, and `PoliceService` stub; custom structure loading,
pasting, loot filling, event hooks, admin structure book, and vanilla catalog;
dialog-driven setup; localization; unit tests.

### Deferred

Authored underworld/sewers; vertical apartments and zoning depth; player-founded
settlements with upkeep and dissolution; black-market NPCs and stock; bounties;
settlement upgrades; concrete structure builds and event hooks; telemetry;
seasons/wipes.

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
  `world/structures/`; only administrators should write files there.
- **Monument pad edits.** Flattening a pad must not spill outside the recorded
  footprint; the pad radius bounds it.
