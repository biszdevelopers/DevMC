# Items

`Items` is the registry-driven successor to ItemLib. It requires Bundler and targets Java 17 / Spigot-compatible 1.20.1 servers.

## Item definitions

Every registry entry is a `DevItem`. Generated vanilla entries use IDs such as `minecraft:diamond`; consumer plugins implement `CustomItem` for entries such as `devmc:test_sword`. A `DevItem` is a singleton definition. `DevItemStack` is the physical Bukkit stack plus its PDC-backed state.

```java
public final class TestSwordItem extends CustomItem {
    public TestSwordItem() {
        super(ItemId.of("devmc", "test_sword"), ItemProperties.builder(Material.DIAMOND_SWORD)
                .maximumStackSize(1).quality(Quality.EPIC)
                .metadata("charges", ItemDataType.INTEGER, 0).build());
    }
}

DeferredItemRegister items = DeferredItemRegister.create(plugin, "devmc");
RegistryObject<TestSwordItem> testSword = items.register("test_sword", TestSwordItem::new);
items.apply(itemRegistry);
```

Resolve an existing stack through `ItemFactory.wrap`. Custom PDC identity has precedence; unknown custom IDs intentionally resolve to `UnresolvedItem`, never to their base material. Vanilla stack definitions are generated eagerly during `ItemsPlugin.onLoad()` for every supported non-air Bukkit item; legacy materials are deliberately excluded.

Override `createBaseStack(int)` when an item needs Bukkit-supported potion, model, or other base metadata. Override `renderName`, `renderLore`, `onCreated`, and `onLoaded` for per-item behavior. Rendering uses Bundler's locale service when a matching translation exists and safely falls back to the item ID otherwise.

## Enchantments

Vanilla Bukkit enchantments are registered as `VanillaEnchantment` definitions during Items startup and continue to be executed by Minecraft. Plugins can register a `CustomEnchantment` through `DeferredEnchantmentRegister`; custom levels are stored in ItemLib PDC data while vanilla levels remain in Minecraft's normal tags.

```java
public final class ExampleEnchantment extends CustomEnchantment {
    public ExampleEnchantment() {
        super(EnchantmentId.of("devmc", "example"),
            EnchantmentProperties.builder().quality(Quality.EPIC).build());
    }
}

DeferredEnchantmentRegister enchantments = DeferredEnchantmentRegister.create(plugin, "devmc");
enchantments.register("example", ExampleEnchantment::new);
enchantments.apply(ItemsPlugin.instance().enchantments());
```

`DevItemStack.enchant(...)` applies a definition at levels 1–3999. `/devenchant <namespace:id> <level>` is an operator/admin debug command for the item in the sender's main hand. Custom enchantment books can be applied through anvils; matching levels increase by one and other pairs retain the higher level.

Applied enchantments may also carry per-item Boolean or Integer metadata through `EnchantmentData` and the metadata-aware `DevItemStack.enchant(...)` overload. Metadata is stored independently from native and custom enchantment levels. `enchants:extra_roll=true` is rendered as a yellow pencil-prefixed enchantment line while retaining ItemLib's normal quality, level, and localized-name ordering. Anvils retain only keys present on both inputs, merging booleans with AND and integers with the minimum value.

Items also registers `items:rainbow` as a built-in visual test enchantment. It has no gameplay effect and renders every character of its lore line with the standard Minecraft rainbow color sequence.

## Threading and data

Registry setup, Bukkit stack reads/writes, rendering, and inventory operations are main-thread operations. Item state uses Bukkit Persistent Data Containers only. The serializer uses a versioned Bukkit byte envelope and deliberately does not read historical NBT or ItemLib payloads.

## Commands and migration

`/giveitem <player> <namespace:item> [amount]` is available with `items.give`; Bukkit also exposes it as `/devcommand:giveitem`. Historical types, old NBT tags, reflection-driven item handlers, NMS/CraftBukkit internals, and old test items are not supported. Use concrete `ItemRegistry`, `ItemFactory`, and `DeferredItemRegister` instead.
