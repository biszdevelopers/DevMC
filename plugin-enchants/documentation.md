# trueMC — Custom Enchanting & XP System

A total rework of Minecraft's enchanting and experience system built around a **socket** mechanic. Every item has a fixed number of enchantment sockets determined by the material it is made from. Enchanting tables, anvils, and grindstones can only add, modify, or remove **one** enchantment at a time.

XP is **linear** — every level costs the same amount of XP. The cost of enchanting is **constant** per item, determined by the material it is made from.

- **Anvil repairs** never become "Too Expensive" — the cap is set by the item's cost.
- **Socket display** — sockets render as centered, equal-width brackets, e.g. `[ Lethality V ]` / `[ empty ]`. Long enchant names use short forms (e.g. "Proj. Prot."). The bracket color identifies the category. Brackets use the game's built-in monospace font so they line up exactly (no resource pack needed).

---

## Display & resource pack

- `config.yml` → `display`:
  - `custom-font` (default `true`) — render socket brackets with the **`minecraft:mono`** monospaced font (from the *Minecraft Seven Mono* / "mono7" pack, CC-BY-4.0) so equal-width brackets align exactly. Set to `false` to use the default proportional font (which can misalign short/empty sockets).
  - `damage-indicators` (default `true`) — floating damage numbers (deduped per entity).
  - `xp-action-bar` (default `true`) — flash `Level N — X/Y XP` on XP gain.
- `config.yml` → `server.resource-pack` — the plugin bundles the pack, serves it over HTTP (`localhost:9090/resource-pack.zip`), and **sends it to every player on join** so it loads automatically:
  - `enabled` (default `true`), `port` (default `9090`),
  - `url` (default `""` → auto `http://localhost:<port>/resource-pack.zip`; set it to the machine's LAN IP/URL if clients connect from other machines),
  - `required` (default `true` — force the pack, disconnecting those who decline; set `false` to let them opt out).
- The pack (mono7 monospace font) lives in `resource-pack/` and is bundled into the jar. `scripts/build-pack.ps1` also produces a standalone `resource-pack.zip` for manual/offline install.

---

## Socket display format
Note that this only defines the format not the color

[Enchantment]
Wrapped description with optional perk metadata here

### Examples

[Efficiency V]
Adds +N ⛏ Mining Efficiency to the item

## Enchantments
New enchantments only
| Enchantment              | Description (display sth similar to this on the lore)                              |
| --------------------- | ----------------------------------------------------------------------------------------------------- |
| Lethality (Non-projectiles)            | Increases ⚔ Damage of the tool by 1/1.5/2/2.5/3 |
| Lethality (Projectiles)            | Increases ⚔ Damage of the tool by 50/75/100/125/150% |
| Penetration (Maces)    | Decreases the target's ⛨ Protection by 15/30/45/60/75% (which exists on the next version).  |
| Penetration (Bows)    | Allows the projectile to shoot through 1/2/3/4/5 target(s). |
| Inflame (Non-Projectile) | Puts the target on fire for 4/8 seconds |
| Inflame (Projectiles) | Puts the target on fire for 3/5 seconds |

Note some enchantments are aligned with minecraft vanilla's calaulation methods, such as letahlity on non-projetiles which is equiavlent ot sharpness
Some others. u can determin the modifier but stick closely to vanilla if possible
**Knockback** — melee knockback + arrow knockback (was Knockback, Punch).
  - **Acrobatics** — mace smash launch + spear reach (was Wind Burst, Lunge).
  - **Winged** — fall damage reduction + double jump (was Featherweight).
  - **Nimble** — draws, swings, and throws come faster (was Quick Charge).

## Enchantment categories

Every enchantment belongs to a category. Sockets are category-typed: a socket can only hold an enchantment from its category.

Some enchantments are **item-specific** — the enchanting table and anvil only offer them on the matching item (e.g. Penetration is a mace/bow/crossbow Lethality enchant, Acrobatics is a mace & spear Prowess enchant).

| Category              | Enchantments                                                                                          | Color  |
| --------------------- | ----------------------------------------------------------------------------------------------------- | ------ |
| Lethality             | Lethality, Penetration                                                                     | Red    |
| Prowess               | Inflame, Looting, Knockback, Nimble, Acrobatics (mace, spear), Multishot (bow, crossbow, trident) | Purple |
| Protection            | Protection, Impact Resistance                                                                         | Green  |
| Mobility (boots only) | Depth Strider, Frost Walker, Soul Speed, Swift Sneak, Winged                                         | Blue   |
| Tide                  | Aqua Affinity (helmet), Respiration (helmet) — Riptide, Loyalty, Channeling (trident & rod)           | Cyan   |
| Efficiency            | Efficiency                                                                                            | Blue   |
| Harvesting            | Fortune, Silk Touch                                                                                   | Pink   |
| Sustainability        | Mending, Unbreaking                                                                                   | Yellow |

Revised 

| Category                 | Enchantments                                                                                          | Color  |
| ------------------------ | ----------------------------------------------------------------------------------------------------- | ------ |
| ⚔ Fatality              | Lethality, Penetration                                                                                | Red §c    |
| ✦ Prowess               | Inflame, Looting, Knockback, Nimble, Acrobatics (mace, spear), Multishot (bow, crossbow, trident)     | Magenta §d |
| ⛨ Protection            | Protection, Impact Resistance                                                                         | Lime §a  |
| ☁ Mobility (boots only) | Depth Strider, Frost Walker, Soul Speed, Swift Sneak, Winged                                          | Cyan §b  |
| ☀ Tide                  | Aqua Affinity (helmet), Respiration (helmet) — Riptide, Loyalty, Channeling (trident & rod)           | Blue §9   |
| ⛏ Harvesting            | Fortune, Silk Touch, Efficiency                                                                       | Gold §6   |
| ♻ Sustainability        | Mending, Unbreaking                                                                                   | Yellow §e |


> Every item has one implicit Sustainability socket, so it is omitted from the table below unless the item has more than one.

---

## Item sockets

| Item                       | Sockets                                                        | Cost (levels) |
| -------------------------- | ------------------------------------------------------------- | ------------- |
| Bow                        | Lethality, Prowess                                           | 12            |
| Crossbow                   | Lethality, Prowess                                           | 12            |
| Trident                    | Lethality, Prowess, Tide x2                                  | 15            |
| Fishing rod                | Tide x2, Efficiency, Harvesting x2                            | 10            |
| Wooden items               | No sockets                                                    | —             |
| Leather armor              | Protection                                                    | 15            |
| Chainmail armor            | Protection                                                    | 12            |
| Turtle helmet              | Tide                                                          | 9             |
| Stone sword                | Lethality                                                     | 5             |
| Stone axe                  | Lethality, Efficiency                                         | 5             |
| Stone pickaxe, shovel      | Efficiency                                                    | 5             |
| Stone hoe                  | Harvesting                                                    | 5             |
| Gold helmet                | Protection x2, Tide                                           | 25            |
| Gold chestplate & leggings | Protection x2                                                 | 25            |
| Gold boots                 | Protection x2, Mobility x2                                    | 25            |
| Gold sword                 | Lethality, Prowess x2, Sustainability x2                      | 25            |
| Gold hoe                   | Harvesting, Sustainability x2                                 | 25            |
| Gold axe                   | Lethality, Prowess, Efficiency, Harvesting, Sustainability x2 | 25            |
| Gold pickaxe               | Efficiency x2, Harvesting, Sustainability x2                  | 25            |
| Gold shovel                | Efficiency, Sustainability x3                                 | 25            |
| Iron armor (no boots)      | Protection                                                    | 9             |
| Iron boots                 | Protection, Mobility                                          | 9             |
| Iron sword                 | Lethality, Prowess                                            | 9             |
| Iron axe                   | Lethality, Efficiency, Harvesting                             | 9             |
| Iron pickaxe               | Efficiency, Harvesting                                        | 9             |
| Iron shovel                | Efficiency, Harvesting                                        | 9             |
| Iron hoe                   | Harvesting                                                    | 9             |
| Copper tools & armor       | Mirrors iron (sword, axe, pickaxe, shovel, hoe, armor)        | 8             |
| Diamond tools              | Iron tool layout plus one Sustainability socket               | 10            |
| Diamond armor              | Exact copy of iron, but boots have no Mobility                 | 10            |
| Netherite armor            | Protection                                                    | 15            |
| Netherite boots            | Protection, Mobility                                          | 15            |
| Netherite sword            | Lethality, Prowess x2                                         | 15            |
| Netherite axe              | Lethality, Prowess, Harvesting                    | 15            |
| Netherite hoe              | Lethality x3, Prowess x3, Harvesting                          | 15            |
| Netherite shovel           | Harvesting                                        | 15            |
| Mace                       | Lethality, Prowess                                            | 15            |
| Wooden spear               | No sockets                                                    | —             |
| Stone spear                | Lethality, Prowess                                            | 5             |
| Copper spear               | Lethality, Prowess                                            | 8             |
| Golden spear               | Lethality, Prowess                                            | 25            |
| Iron spear                 | Lethality, Prowess                                            | 9             |
| Diamond spear              | Lethality, Prowess                                            | 10            |
| Netherite spear            | Lethality, Prowess                                            | 15            |

Socket display order should be in the category order specified above.

version 2


## Item sockets

| Item                       | Sockets                                                       | Cost (levels) |
| -------------------------- | ------------------------------------------------------------- | ------------- |
| Bow                        | Lethality, Prowess                                           | 12            |
| Crossbow                   | Lethality, Prowess                                           | 12            |
| Trident                    | Lethality, Prowess, Tide x2                                  | 15            |
| Fishing rod                | Tide x2, Harvesting x2                                         | 10            |
| Wooden items               | No sockets                                                    | —             |
| Leather armor              | Protection                                                    | 15            |
| Chainmail armor            | Protection                                                    | 12            |
| Turtle helmet              | Tide                                                          | 9             |
| Stone sword                | Lethality                                                     | 5             |
| Stone axe                  | Lethality, Efficiency                                         | 5             |
| Stone pickaxe, shovel      | Efficiency                                                    | 5             |
| Stone hoe                  | Harvesting                                                    | 5             |
| Gold helmet                | Protection x2, Tide                                           | 25            |
| Gold chestplate & leggings | Protection x2                                                 | 25            |
| Gold boots                 | Protection x2, Mobility x2                                    | 25            |
| Gold sword                 | Lethality, Prowess x2, Sustainability x2                      | 25            |
| Gold hoe                   | Harvesting, Sustainability x2                                 | 25            |
| Gold axe                   | Lethality, Prowess, Efficiency, Harvesting, Sustainability x2 | 25            |
| Gold pickaxe               | Efficiency x2, Harvesting, Sustainability x2                  | 25            |
| Gold shovel                | Efficiency, Sustainability x3                                 | 25            |
| Iron armor (no boots)      | Protection                                                    | 9             |
| Iron boots                 | Protection, Mobility                                          | 9             |
| Iron sword                 | Lethality, Prowess                                            | 9             |
| Iron axe                   | Lethality, Efficiency, Harvesting                             | 9             |
| Iron pickaxe               | Efficiency, Harvesting                                        | 9             |
| Iron shovel                | Efficiency, Harvesting                                        | 9             |
| Iron hoe                   | Harvesting                                                    | 9             |
| Copper tools & armor       | Mirrors iron (sword, axe, pickaxe, shovel, hoe, armor)        | 8             |
| Diamond tools              | Iron tool layout plus one Sustainability socket               | 10            |
| Diamond armor              | Exact copy of iron, but boots have no Mobility                 | 10            |
| Netherite armor            | Protection                                                    | 15            |
| Netherite boots            | Protection, Mobility                                          | 15            |
| Netherite sword            | Lethality, Prowess x2                                         | 15            |
| Netherite axe              | Lethality, Prowess, Efficiency, Harvesting                    | 15            |
| Netherite hoe              | Lethality x3, Prowess x3, Harvesting                          | 15            |
| Netherite shovel           | Efficiency, Harvesting                                        | 15            |
| Mace                       | Lethality, Prowess                                            | 15            |
| Wooden spear               | No sockets                                                    | —             |
| Stone spear                | Lethality, Prowess                                            | 5             |
| Copper spear               | Lethality, Prowess                                            | 8             |
| Golden spear               | Lethality, Prowess                                            | 25            |
| Iron spear                 | Lethality, Prowess                                            | 9             |
| Diamond spear              | Lethality, Prowess                                            | 10            |
| Netherite spear            | Lethality, Prowess                                            | 15            |

## Cost model

Enchanting cost is derived from the item's **enchantability score**, which is also the per-enchant level cost (see `costs.yml`). Higher enchantability = higher cost per enchant.

| Material  | Cost (levels) | Notes                                                |
| --------- | ------------- | ---------------------------------------------------- |
| Wood      | 0             | No sockets                                           |
| Stone     | 5             | Cheapest, sparsest sockets                           |
| Copper    | 8             | Between stone and iron                               |
| Iron      | 9             |                                                      |
| Turtle    | 9             |                                                      |
| Diamond   | 10            | Mirrors iron sockets                                 |
| Chainmail | 12            |                                                      |
| Leather   | 15            |                                                      |
| Netherite | 15            |                                                      |
| Gold      | 25            | Highest cost & most sockets — a magical glass cannon |

Bow, crossbow, trident, fishing rod, and mace have hand-tuned costs (12 / 12 / 15 / 10 / 15). All values are configurable in `costs.yml`.

---

## Enchantment changes

- **Combined & renamed** — several enchantments were merged:
  - **Lethality** — melee + ranged + mace damage (was Sharpness, Power, Density).
  - **Penetration** — projectiles pierce and attacks negate a portion of armor (was Piercing, Breaching).
  - **Inflame** — melee + arrow ignition (was Fire Aspect, Flame).
  - **Knockback** — melee knockback + arrow knockback (was Knockback, Punch).
  - **Acrobatics** — mace smash launch + spear reach (was Wind Burst, Lunge).
  - **Winged** — fall damage reduction + double jump (was Featherweight).
  - **Nimble** — draws, swings, and throws come faster (was Quick Charge).

- **Impact Resistance** — reduces fall, explosion, mace, and spear damage. Pure custom effect (no longer maps to vanilla Feather Falling).

- **Protection** — combines a tiny bit of fire and projectile protection.

- **Gold bonus** — gold items greatly amplify stat-increasing enchantments (e.g. Lethality deals 2 damage at level I and +1 per subsequent level).

- **Unbreaking** — increases max durability and stops durability loss on weak usage.

- **Mending** — uses collected experience to repair the item; every mended point is tracked, raising Mending from I to XI (x1.0 to x2.0 repair per orb). Tier thresholds are cumulative and grow exponentially: 2,000 mends for II; 20,000 each for III–V; 50,000 each for VI–VIII; 100,000 for IX; 200,000 for X; and 400,000 for XI (912,000 mends total).

- **Grindstone** — stripping a socket refunds 80% of the XP cost.

- **Enchanted books** — books are obtained the vanilla way (enchanted at a real enchanting table, combined at an anvil, or found in loot) and just work with the system: the anvil applies them into sockets. Non-socketable items use the vanilla table/anvil/grindstone normally; only socketable items use trueMC's dialogs.

- **Ranged cross-compat** — Lethality, Inflame, and Knockback work on bows, crossbows, and tridents via custom projectile behaviour (their vanilla counterparts only cover melee). **Penetration** negates armor on all melee and ranged hits, and lets the projectile pierce (bow/trident). **Multishot** also pierces the single projectile to hit more targets — no extra arrows or tridents are ever spawned; crossbows keep the vanilla volley. **Nimble** works on every weapon with no damage bonus: melee swings cool down faster (attack-speed), tridents throw faster, crossbows load faster natively, and bows draw **25% faster per level** — the server advances the draw ticks, so you can release early and still fire a full-power shot (no damage is added). The bundled `resource-pack/` (build with `scripts/build-pack.ps1`) lowers the bow's pull-animation thresholds so the draw *looks* as fast as it plays. Infinity has been removed. Multishot and Penetration are item-restricted so they only appear where they function.

- **Duplicate sockets** — only **Efficiency** may occupy multiple sockets. Its effective level is the sum across those sockets, capped at max level. Each Efficiency copy still displays in its own socket. Legacy duplicate copies of other enchantments are collapsed into one socket while preserving their summed level.

- **Riptide fishing rods** — Riptide grants the displayed **Grapple** ability. Reeling an empty or grounded hook pulls the player toward it only while the player is in water or exposed to rain. Snowfall and dry biomes do not activate it. Each successful pull consumes durability using vanilla Unbreaking probability and clears accumulated fall distance.

- **Shortbow ammunition** — while a Nimble bow is equipped in either hand, arrow
  stacks are represented by temporary dummy ammo. This deliberately hides the
  arrows from Minecraft's vanilla bow-use detector, preventing the normal
  charge-and-release shot from starting beside Shortbow's custom instant shot.
  Each dummy stores a serialized one-item template, so the original arrow type,
  potion/item metadata, and count are restored when the bow is unequipped.

### Enchantment config schema

`enchantments.yml` entries support **multi-mapping** and **item grouping**:

- `vanilla` may be a single key or a list — each maps to a real Minecraft enchantment. e.g. `lethality` → `[ sharpness, power, density ]`, `penetration` → `[ piercing, breach ]`. The enchant gains all of those vanilla effects, and any vanilla book/enchant matching any of them converts to the trueMC enchant.
- `items` is a list of item *groups*, matched by the item's base type (tier and `fishing_`/`turtle_` prefixes stripped): `rod` matches `fishing_rod`, `helmet` matches `turtle_helmet`, `sword` matches any `*_sword`, `bow` matches `bow` but **not** `crossbow`. Empty/absent means "any item".

---

## Fishing system

Fishing is a fully reworked, deterministic loop (the active build). It is data-driven and modular.

**Fixed-time auto-catch** — casting a rod starts a countdown (`fishing.yml` → `base-time`, shipped default 15s). When it ends, the catch is **automatic** — no right-click needed. The vanilla bite/reel loop is bypassed.

- **Efficiency** directly shortens the countdown: `delay = base × (1 − 0.15 × level)` (Lure's role is folded in).
- **Reeling early**, the hook landing in ground, or hooking a mob cancels the catch and yields nothing.
- A **countdown indicator** floats above the bobber (`⏳ 5s → ⏳ 4s → …`) and doubles as the AFK notification.

**Loot roll** — driven by `fishing.yml` (weighted fish/treasure tables, reloadable):

- **Silk Touch** → treasure only (no fish).
- Else `rand < treasure-chance` → treasure, else fish.
- **Fortune** multiplies the loot amount (`×level`, cap 64) — fish **and** treasure.

**Loyalty** — an AFK-fishing enchant (trident & rod): after each catch the bobber stays and the next countdown starts automatically, so loot keeps flowing with no input.

**Channeling** (rod) — each catch worsens the weather until a thunderstorm starts; lightning damage scales with level. **Riptide** (rod) — grappling pull toward the hook in water/rain.

---

## Experience

- XP gain is unchanged at the source (mobs, mining, smelting, fishing, bottles) but the level curve is now **linear**: every level requires **17 XP**, equal to vanilla level 5 to 6.
- Enchanting cost is normalized by material weight to **5–10 levels**, with a small randomized offset clamped to that range.

## Status / open topics

- Cost values are configurable in `costs.yml`; the defaults are listed in the tables above.
- Wooden items currently have no sockets — a transitional tier; a future design may give them a niche.
- Spears exist as socket layouts and enchants (Acrobatics) but have no item source yet — they will be added with the custom item system.
- Fishing loot tables are fully modular (`fishing.yml`) and will be expanded over time.
