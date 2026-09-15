# Item Lore Display Standard

This document is the shared display contract for ItemLib items and plugins that
render through ItemLib. User-visible labels and templates must come from
Bundler locale resources and must also be installed in the active ServerData
language directory.

## Section order and spacing

Item lore is rendered in this order:

1. Item quality, tradeability, and category metadata.
2. Enchantments or enchantment sockets.
3. Declarative abilities.
4. Item-specific lore.
5. Runtime lore.

Use one blank lore line between populated sections and between adjacent ability
blocks. Do not add blank lines between consecutive empty sockets. A filled
socket is a distinct display block: add one blank line above it when a socket
exists above and one below it when a socket exists below. Adjacent filled
sockets share their intervening separator rather than producing two blanks.

## Description text

Enchantment and ability descriptions use gray (`§7`) text and ItemLib's shared
locale-sensitive wrapping width. Statistical numbers are highlighted after
localization and before wrapping:

- Positive values, explicitly positive values, and unsigned values use green
  (`§a`). Examples: `5`, `+5`, `12.5%`, `2x`, and the numeric part of `3.0s`.
- Negative values use red (`§c`). Examples: `-5`, `−2.5%`.
- The renderer restores gray (`§7`) immediately after each highlighted value.
- Formatting-code digits such as the `7` in `§7` are never treated as values.
- Numeric ranges treat both endpoints as positive unless an endpoint explicitly
  carries a negative sign.

These rules apply only to description prose. Enchantment levels, socket icons,
ability names, cooldown rows, and activation rows retain their dedicated colors.
When an enchantment has different mechanics on different item families, its
description must describe only the mechanics available on the rendered item;
for example, bows show projectile effects rather than melee or mace effects.
Books are the exception because they are not bound to an item family: enchanted
books and enchanting-book offer previews show the complete description,
including every supported item-family variant.

## Enchantments and typed sockets

Normal enchantments are ordered by quality descending, level descending, then
localized name. Their names use the enchantment quality color. Descriptions are
shown when no more than five enchantments are present.

Typed socket brackets and icons use the socket category color. The enchantment
name uses its quality color, followed by its Roman-numeral level. Empty sockets
are compact consecutive lines. Filled sockets include their wrapped description
and begin a distinct block when earlier socket content exists.

Efficiency is the only enchantment that may occupy repeated sockets. Each copy
keeps its own socket line and level, while the underlying Efficiency level is
the sum of all copies so its gameplay effect stacks predictably.

## Abilities

Abilities are immutable, display-only definitions exposed through `AbilityItem`.
They do not own callbacks, listeners, ticking, or runtime state. Ability
membership should be derived from current item data whenever possible.

Active abilities render a quality-colored localized name, wrapped description,
green one-decimal cooldown, and yellow localized usage method. A zero-second
cooldown is displayed as the localized green `Instant!` label instead of
`0.0s`. Passive abilities
render the name and description followed only by a yellow one-decimal activation
interval and localized hand or inventory context.

When an ability has an action-bar status display, use the same localized ability
name and quality color as its lore definition; do not substitute the owning
enchantment's name or a separately hard-coded color.

## Localization

Names, descriptions, usage methods, socket labels, cooldown templates, passive
activation templates, and other player-visible strings require locale keys.
Fallbacks are allowed for API resilience but are not a substitute for bundled
and active ServerData locale entries.
