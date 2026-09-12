package dev.bisz.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

final class VanillaItemOverrideTest {

  private static final ItemId WOODEN_SWORD = ItemId.of(
    "minecraft",
    "wooden_sword"
  );

  @Test
  void overrideRequiresTheVanillaCatalog() {
    ItemRegistry registry = new ItemRegistry();
    assertThrows(
      IllegalStateException.class,
      () -> registry.registerVanillaOverride(plugin(), new TestWoodenSword())
    );
  }

  @Test
  void classOverrideReplacesAndRestoresGeneratedDefinition() {
    ItemRegistry registry = new ItemRegistry();
    registry.registerVanillaItems();
    DevItem generated = registry.get(WOODEN_SWORD).orElseThrow();
    Plugin owner = plugin();
    TestWoodenSword override = new TestWoodenSword();

    registry.registerVanillaOverride(owner, override);

    assertSame(override, registry.get(WOODEN_SWORD).orElseThrow());
    assertTrue(registry.hasVanillaOverrides(WOODEN_SWORD));
    assertTrue(override.vanilla());
    assertTrue(override.behaviorsEnabled());
    assertFalse(generated.behaviorsEnabled());
    assertThrows(
      IllegalArgumentException.class,
      () -> registry.registerVanillaOverride(plugin(), new TestWoodenSword())
    );

    registry.unregisterAll(owner);

    assertSame(generated, registry.get(WOODEN_SWORD).orElseThrow());
    assertFalse(registry.hasVanillaOverrides(WOODEN_SWORD));
  }

  @Test
  void overridePropertiesMustUseTheSameMaterial() {
    ItemProperties stone = ItemProperties.builder(Material.STONE_SWORD).build();
    assertThrows(
      IllegalArgumentException.class,
      () -> new OverrideVanillaItem(Material.WOODEN_SWORD, stone) {}
    );
  }

  @Test
  @SuppressWarnings("deprecation")
  void legacyOverrideStillMarksTheVanillaDefinition() {
    ItemRegistry registry = new ItemRegistry();
    registry.registerVanillaItems();
    registry.addVanillaOverride(WOODEN_SWORD, stack -> {});
    assertTrue(registry.hasVanillaOverrides(WOODEN_SWORD));
  }

  @Test
  void builtInWoodenSwordDeclaresItsCounterAndAttackHook() {
    WoodenSwordOverride override = new WoodenSwordOverride();
    ItemMetadata counter = override
      .properties()
      .metadata()
      .get(WoodenSwordOverride.ATTACK_TIMES);

    assertSame(ItemDataType.INTEGER, counter.type());
    assertEquals(0, counter.defaultValue());
    assertTrue(override.properties().attackTriggering());
    assertEquals(1, WoodenSwordOverride.nextAttackCount(0));
    assertEquals(
      Integer.MAX_VALUE,
      WoodenSwordOverride.nextAttackCount(Integer.MAX_VALUE)
    );
  }

  private static Plugin plugin() {
    return (Plugin) Proxy.newProxyInstance(
      Plugin.class.getClassLoader(),
      new Class<?>[] { Plugin.class },
      (proxy, method, arguments) -> null
    );
  }

  private static final class TestWoodenSword extends OverrideVanillaItem {

    TestWoodenSword() {
      super(
        Material.WOODEN_SWORD,
        ItemProperties.builder(Material.WOODEN_SWORD)
          .attackTriggering(true)
          .build()
      );
    }
  }
}
