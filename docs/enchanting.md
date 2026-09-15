# trueMC — Custom Enchanting, Socket & XP System

> **Single source of truth** for the merged enchanting system. Supersedes
> `TrueMC/documentation.md`, `TrueMC/qol-plan.md`, and
> `plugin-enchants/documentation.md`.

trueMC is a total rework of Minecraft's enchanting and experience system built
around a **socket** mechanic. Every item has a fixed number of enchantment
sockets determined by the material it is made from. Enchanting tables, anvils,
and grindstones add, modify, or remove sockets.

The system ships as `plugin-enchants` inside the DevMC plugin suite and runs on
**Spigot 1.20.1** with the shared **Bundler** and **Items** plugins. It is a
port of the standalone Paper `TrueMC` plugin; content that only exists on 1.21+
(mace, spears, copper gear, Density, Breach, Wind Burst, Lunge) is retained as
forward-compatible configuration that no-ops on 1.20.1.

## Platform and build

- Java 17, Spigot API 1.20.1, Maven.
- Depends on `Bundler` (locales, menus, JSON profiles) and `Items` (item and
  enchantment registries, rendering, anvil merge).
- Build the whole suite from the repository root: `mvn clean package`. Reactor
  order is Bundler → Items/Currency → Enchants → Combat → SMP.

## Data model

- Socket layouts and the enchantment catalog are hardcoded Java tables.
- Costs, fishing loot, and display/XP settings are JSON-backed through
  Bundler's ServerData store (`D:/ServerData/enchants`).
- Item state is stored through the Items PDC:
  - `enchants:socket_entries` — the authoritative ordered socket list
    (`index|enchantmentId|level`, `;`-separated).
  - Items' `custom-enchantments` payload for custom levels and
    `enchantment-metadata` for per-socket metadata.
- Vanilla enchantments that map to a trueMC enchantment (for example Sharpness →
  Lethality) are converted into sockets during normalization.
- Sockets are category-typed: a socket only holds an enchantment from its
  category.

## Configuration files

All configuration lives in Bundler's ServerData store. Bundled defaults are
merged into missing keys on startup, so server edits survive rebuilds.

| File (`D:/ServerData/enchants/`) | Purpose |
| ---- | ------- |
| `config.json` | debug, display toggles, XP rate, cost multiplier/refund, lapis cost |
| `costs.json` | per-material tier and per-item enchant costs |
| `fishing.json` | base time, efficiency reduction, treasure chance, fish/treasure loot tables |

All player-visible strings are provided by Bundler locale resources
(`lang/en_us.json`, plus the other bundled languages) and follow
`docs/item-lore-standard.md`.

## Enchantment categories

| Category | Enchantments | Color |
| -------- | ------------ | ----- |
| ⚔ Fatality | Lethality, Penetration | §c |
| ✦ Prowess | Inflame, Looting, Knockback, Nimble, Acrobatics (mace, spear), Multishot (bow, crossbow, trident) | §d |
| ⛨ Protection | Protection, Impact Resistance | §a |
| ☁ Mobility (boots only) | Depth Strider, Frost Walker, Soul Speed, Swift Sneak, Winged | §b |
| ☀ Tide | Aqua Affinity (helmet), Respiration (helmet), Riptide, Loyalty, Channeling (trident & rod) | §9 |
| ⛏ Harvesting | Efficiency, Fortune, Silk Touch | §6 |
| ♻ Sustainability | Mending, Unbreaking | §e |

## Item sockets

Each material's socket layout is fixed in `SocketLayouts`.

| Item | Sockets | Cost (levels) |
| ---- | ------- | ------------- |
| Bow | Lethality, Prowess | 12 |
| Crossbow | Lethality, Prowess | 12 |
| Trident | Lethality, Prowess, Tide x2 | 15 |
| Fishing rod | Tide x2, Efficiency, Harvesting x2 | 10 |
| Wooden items | No sockets | — |
| Leather armor | Protection | 15 |
| Chainmail armor | Protection | 12 |
| Turtle helmet | Tide | 9 |
| Stone sword | Lethality | 5 |
| Stone axe | Lethality, Efficiency | 5 |
| Stone pickaxe, shovel | Efficiency | 5 |
| Stone hoe | Harvesting | 5 |
| Gold helmet | Protection x2, Tide | 25 |
| Gold chestplate & leggings | Protection x2 | 25 |
| Gold boots | Protection x2, Mobility x2 | 25 |
| Gold sword | Lethality, Prowess x2, Sustainability x2 | 25 |
| Gold hoe | Harvesting, Sustainability x2 | 25 |
| Gold axe | Lethality, Prowess, Efficiency, Harvesting, Sustainability x2 | 25 |
| Gold pickaxe | Efficiency x2, Harvesting, Sustainability x2 | 25 |
| Gold shovel | Efficiency, Sustainability x3 | 25 |
| Iron armor (no boots) | Protection | 9 |
| Iron boots | Protection, Mobility | 9 |
| Iron sword | Lethality, Prowess | 9 |
| Iron axe | Lethality, Efficiency, Harvesting | 9 |
| Iron pickaxe | Efficiency, Harvesting | 9 |
| Iron shovel | Efficiency, Harvesting | 9 |
| Iron hoe | Harvesting | 9 |
| Copper tools & armor | Mirrors iron | 8 |
| Diamond tools | Iron layout plus one Sustainability socket | 10 |
| Diamond armor | Iron layout; boots have no Mobility | 10 |
| Netherite armor | Protection | 15 |
| Netherite boots | Protection, Mobility | 15 |
| Netherite sword | Lethality, Prowess x2 | 15 |
| Netherite axe | Lethality, Prowess, Efficiency, Harvesting | 15 |
| Netherite hoe | Lethality x3, Prowess x3, Harvesting | 15 |
| Netherite shovel | Efficiency, Harvesting | 15 |
| Mace | Lethality, Prowess | 15 |
| Spears | Lethality, Prowess (wooden: none) | 5–25 |

## Cost model

Enchanting cost is constant per item and derived from the material tier (or an
exact `costs.json` item override). The per-material defaults are wood 0, stone 5,
copper 8, iron 9, turtle 9, diamond 10, chainmail 12, leather 15, netherite 15,
gold 25. Bow, crossbow, trident, fishing rod, and mace have hand-tuned costs
(12 / 12 / 15 / 10 / 15). All values are configurable, scaled by
`cost.multiplier`, and refunded at `cost.refund-percent` (default 80%).

## Enchantment changes

- **Combined & renamed** — several enchantments were merged:
  - **Lethality** — melee + ranged + mace damage (was Sharpness, Power, Density).
    Gold items additionally amplify damage enchantments.
  - **Penetration** — projectiles pierce and attacks bypass a portion of the
    target's armor (was Piercing, Breaching).
  - **Inflame** — melee + arrow ignition (was Fire Aspect, Flame).
  - **Knockback** — melee knockback + arrow knockback (was Knockback, Punch).
  - **Acrobatics** — mace smash launch + spear reach (was Wind Burst, Lunge).
    Forward-compatible configuration; inert on 1.20.1.
  - **Nimble** — melee attack speed (+0.25/level), trident throw speed
    (+10%/level), and bows become a Shortbow that fires instantly on left click
    (was Quick Charge). Crossbows keep vanilla Quick Charge.
- **Winged** — halves fall damage and grants one double jump: flight is granted
  while grounded, so a single jump-press launches the player along their
  facing with a fixed upward bias
  (`direction.setY(0.5).normalize().multiply(0.9)`). The double jump has a
  three-second cooldown that also prevents fall damage until landing.
  - **Impact Resistance** — reduces fall, explosion, mace, and spear damage.
- **Multishot** — pierces the single projectile to hit more targets; crossbows
  keep the vanilla volley. No extra projectiles are spawned.
- **Enchanting levels** — the table rolls a random level from I to the
  enchantment's maximum (bookshelves add extra rolls) and presents a single
  offer in the leftmost offer slot; the remaining slots show empty books.
- **Glint** — socketed items show the enchantment glint via a hidden marker
  enchantment, since custom enchantments are stored in ItemLib's PDC payload.
  Fishing rods use Protection as the marker, so a stray Luck of the Sea is
  stripped instead of being mistaken for it.
- **Tooltips** — socket descriptions are item-specific (for example Loyalty
  reads differently on a trident than on a fishing rod) and can be collapsed
  per player with `/enchants tooltips collapse`. Toggling re-renders the
  player's inventory immediately.
- **Mending** — tracked as a mend counter with tiers I–XI; each tier raises the
  durability restored per orb, and anvil repairs cost nothing.
- **Grindstone** — stripping sockets refunds `cost.refund-percent` of the
  enchant cost as experience.
- **Enchanted books** — obtained the vanilla way and applied through the anvil
  into sockets. Their single socket is Universal, so any offered enchantment can
  be placed on it; once filled, the socket presents the placed enchantment's
  category and returns to Universal when emptied; applying a book to a plain
  book converts it into an enchanted book. Books have no material cost, so
  filling their socket costs no experience levels (the table still consumes
  lapis). Non-socketable items keep vanilla table/anvil/grindstone use.
- **Duplicate sockets** — only Efficiency may occupy multiple sockets; its
  effective level is the sum across copies, capped at max level. Legacy
  duplicate copies of other enchantments collapse into one socket.
- **Offer generation** — candidate-first: the table lists every enchantment
  that can still be placed in the selected socket without breaking the rules
  (socket type, material fit, conflicts, duplicate rules) and rolls one of
  them, so a socket always offers one enchantment unless nothing compatible
  remains.
- **Ranged cross-compat** — Lethality, Inflame, Knockback, Penetration, Nimble,
  and Multishot work on bows, crossbows, and tridents. Multishot and Penetration
  pierce the single projectile; no extra projectiles are spawned.

## Fishing system

Fishing is a deterministic, data-driven loop (`fishing.json`):

- The countdown (default 15s, minimum 3s) only starts once the bobber settles in
  water; while it is airborne nothing is fished, and a bobber that settles
  outside water cancels the cast. Efficiency shortens the wait by
  `efficiency-reduction` per level.
- Reeling early, the hook landing in ground, or hooking a mob cancels the catch.
- A floating countdown indicator tracks the bobber.
- The catch flies from the bobber to the player as an item entity and is
  rendered through ItemLib, so treasure overrides become trueMC items.
- Vanilla bobber splash and retrieve sounds play globally at the catch.
- **Silk Touch** → treasure only; otherwise `treasure-chance` decides. **Fortune**
  multiplies the loot amount.
- **Loyalty** keeps the bobber fishing automatically (AFK). **Channeling**
  worsens the weather. **Riptide** grapples the player toward the hook.

## Experience

- The level curve is **linear**: `xp.points-per-level` (default **20**) XP per
  level, configurable in `config.json`. `LinearExperience` is the shared API and
  is consumed by `plugin-combat`.
- XP gain is unchanged at the source.

## Display

- Sockets render as centered, equal-width brackets, e.g. `[ Lethality V ]` /
  `[ empty ]`; filled brackets use the category color and empty sockets render
  gray. Layout follows `docs/item-lore-standard.md`.
- Occupied sockets show an ender eye icon in the enchanting table and station
  menus.
- Floating damage numbers are deduplicated per entity
  (`display.damage-indicators`).

## Commands

`/enchants <give|info|xp|tooltips|reload>` (permission `enchants.admin`, OP +
Bundler `ADMIN`):

- `give <material>` — receive a socketed item.
- `info` — inspect the sockets on the held item and its cost/refund.
- `xp <add|set|get> [amount]` — manage linear XP.
- `tooltips <collapse|expand|toggle>` — show or hide socket descriptions on
  this player's item tooltips.
- `reload` — reload the ServerData JSON configuration and rebuild the costs.

`/devenchant <namespace:id> <level> [key=value ...]` (Items) applies any
enchantment directly and requires only operator status.

## Anvil & grindstone stations

Right-clicking an **anvil** or **grindstone** always opens the trueMC station
menu, which shares the enchanting table's design: the same gray and black frame,
a storage cell for the item, and the same centered socket grid. Empty sockets
render as category-colored **dyes**, filled sockets as **ender eyes** whose stack
size is the enchantment level, and every label and message is localized. Placing
or removing an item in the cell updates the grid immediately. Vanilla anvil and
grindstone behavior is replaced at these blocks.

- **Anvil** — each empty socket is a storage cell showing its dye. Hold a
  matching enchanted book on the cursor and place it on an empty socket to fill
  that socket, consuming the book and the item's enchant cost in levels.
  Mismatched books, surplus copies, and placements on occupied sockets are
  returned to the player. A **Repair** button restores durability: free with
  Mending, otherwise one repair material plus the enchant cost.
- **Grindstone** — click a filled socket (ender eye) to strip only that socket,
  refunding `cost.refund-percent` of the enchant cost as experience. Empty
  sockets cannot be clicked.

A localized "How it works" book sits next to the close button in both menus.

## Status / open topics

- Cost values are configurable in `costs.json`; defaults are listed above.
- Wooden items currently have no sockets — a transitional tier.
- Spears, the mace, and copper gear are forward-compatible config; they require
  a 1.21+ server to exist.
- Nimble accelerates melee attack speed and trident throws, and turns bows into
  a Shortbow; crossbow loading still uses vanilla Quick Charge only.
