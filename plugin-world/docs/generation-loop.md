# World generation loop

Status: authoritative. This document defines the order in which the `world`
plugin builds a world and how it keeps that world in shape afterwards. It
supersedes the earlier rough notes; `design.md` describes the wider gameplay
systems built on top of this loop.

## 0. Concepts

- **Barebone state** — the terrain-only form of a chunk: bedrock, stone and
  deepslate, water, and surface blocks. No ores, plants, animals, or structures.
  This is the state that chunk regeneration always pushes terrain back to.
- **Features** — non-structure content added on top of the barebone state:
  ores, plants, and animals.
- **Structures** — authored content placed after features are configured.
  Structures are either **monuments** or **POIs** (points of interest).
- **Settlement** — a safe, policed set of chunks. Every chunk is wilderness by
  default; settlements are carved out by an administrator.
- **Wilderness** — every chunk that is not part of a settlement. It is
  regenerated continuously and cannot be permanently built in.

## 1. First-time setup

Setup is driven by dialogs and only runs while the world has not been built yet.

1. When a player joins and setup is incomplete:
   - If the player is **not** an operator, show a notice dialog explaining that
     an operator must complete world setup.
   - If the player **is** an operator, show the setup dialog.
2. The setup dialog asks the operator to confirm that they want to generate a
   `devMc` world, and collects:
   - the **seed** (text),
   - the **world size** (number),
   - the remaining configuration (world name, environment, world type,
     difficulty, and gamerules) using the configured defaults.
3. On confirmation the plugin creates or adopts the world, applies gamerules and
   difficulty, sets the spawn and world border, and records that setup has run.
   Re-running setup is safe and idempotent.

## 2. Barebone generation

After the world exists, chunks are generated until the configured world area has
reached the completed **barebone state**. Barebone generation is terrain only:

- bedrock layer,
- stone and deepslate below the surface,
- water to sea level,
- grass/dirt/sand surface blocks by biome.

Ores, decorations, animals, and structures are all suppressed during this stage.
The resulting terrain is what regeneration later restores chunks to.

## 3. Feature generation

Once the world is barebone, features are generated over it. Features are
**plants, ores, and animals** — never structures.

### 3.1 Ores

Ore generation is vanilla-like in vein size and height distribution, but
rebalanced:

- **Overall increase** in ore volume compared with vanilla.
- **Nerf vein mining, buff caving** — ore is biased toward positions that are
  already exposed to air (cave walls and floors), so exploring caves is the
  efficient way to gather and strip mining is comparatively weak.
- **Fewer unexposed veins** — veins that would be completely buried are reduced
  or relocated toward nearby open space.

The earlier 4×4 chunk grouping for ore/regen is gone; ore and regeneration are
tracked per chunk.

### 3.2 Plants

Biome-appropriate vegetation is generated: trees, grass, flowers, and other
surface decoration matching each sector's biome.

### 3.3 Animals

Passive animals are generated on suitable surface terrain, at a modest density,
so the wilderness supports hunting and farming without becoming crowded.

## 4. Structure configuration

After features exist, the operating player is asked to start the **structure
configuration process**.

1. The operator receives an **admin structure book** (also obtainable later by
   command). The book lists every structure with:
   - its type — **monument** or **POI**,
   - the biome(s) it belongs in,
   - its category and other metadata.
2. Clicking an entry opens a **preview**: the structure is pasted into its own
   slot in the **storage and testing dimension** and the operator is teleported
   there. Every structure is stored as a schematic **file**.
3. The operator edits the preview and saves it. Saving captures the edited
   region (the WorldEdit selection when present) back to the structure's file.
   A previewed vanilla structure is written to a new file and registered as a
   custom definition.
4. Minecraft's original structures are loaded into the catalog by default; the
   operator may also add custom structures from schematics.
5. When configuration is confirmed, the plugin proceeds to generate structures.

## 5. Structure generation

Structures are placed according to their type.

### 5.1 Monuments

- Spawn **once** each.
- Are **spaced out** so they do not crowd one another.
- Must not be covered up or distort surrounding terrain: a configurable **pad**
  is cleared/flattened around the footprint before pasting.
- Are persistent and **exempt from regeneration**; only their loot refreshes.

### 5.2 POIs

- May spawn **many times**, up to a global **cap**.
- Are placed as part of the per-chunk feature pass.
- **Despawn and respawn** together with the loot and ore regeneration cycle.

## 6. Settlement zoning

Every chunk is **wilderness** by default. A settlement is defined by an
administrator selecting chunks with **WorldEdit**; the selection is converted to
the settlement's chunk set. Manual chunk add/remove remains available as a
fallback.

## 7. Ongoing regeneration

- Regeneration is tracked and scheduled **per chunk** (no 4×4 groups).
- A chunk is restored to its **barebone** state, then features are re-applied
  and its POIs are re-rolled.
- Monuments are never regenerated.
- Chunks inside a settlement are never regenerated.
