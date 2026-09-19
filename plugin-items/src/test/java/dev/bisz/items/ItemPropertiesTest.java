package dev.bisz.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

final class ItemPropertiesTest {

  @Test
  void attackTriggerIsOptIn() {
    assertFalse(
      ItemProperties.builder(Material.WOODEN_SWORD).build().attackTriggering()
    );
    assertTrue(
      ItemProperties.builder(Material.WOODEN_SWORD)
        .attackTriggering(true)
        .build()
        .attackTriggering()
    );
  }

  @Test
  void modelDefaultsToUnset() {
    ItemProperties properties = ItemProperties.builder(Material.DIAMOND_SWORD)
      .build();
    assertNull(properties.itemModel());
    assertFalse(properties.hasCustomModelData());
    assertEquals(List.of(), properties.customModelDataFloats());
    assertEquals(List.of(), properties.customModelDataFlags());
    assertEquals(List.of(), properties.customModelDataStrings());
    assertEquals(List.of(), properties.customModelDataColors());
  }

  @Test
  void itemModelAcceptsStringAndKey() {
    ItemProperties parsed = ItemProperties.builder(Material.DIAMOND_SWORD)
      .itemModel("devmc:item/test_sword")
      .build();
    assertEquals(
      new NamespacedKey("devmc", "item/test_sword"),
      parsed.itemModel()
    );
    NamespacedKey key = new NamespacedKey("devmc", "item/test_axe");
    assertEquals(
      key,
      ItemProperties.builder(Material.DIAMOND_AXE).itemModel(key).build().itemModel()
    );
  }

  @Test
  void itemModelStringParsingRules() {
    assertEquals(
      new NamespacedKey("minecraft", "item/test_sword"),
      ItemProperties.builder(Material.DIAMOND_SWORD)
        .itemModel("item/test_sword")
        .build()
        .itemModel()
    );
    assertThrows(
      IllegalArgumentException.class,
      () ->
        ItemProperties.builder(Material.DIAMOND_SWORD)
          .itemModel("devmc:item model")
    );
    assertThrows(
      NullPointerException.class,
      () -> ItemProperties.builder(Material.DIAMOND_SWORD).itemModel((String) null)
    );
  }

  @Test
  void integerCustomModelDataBecomesFloat() {
    ItemProperties properties = ItemProperties.builder(Material.DIAMOND_SWORD)
      .customModelData(7)
      .build();
    assertTrue(properties.hasCustomModelData());
    assertEquals(List.of(7.0f), properties.customModelDataFloats());
  }

  @Test
  void fullCustomModelDataComponentIsRetained() {
    ItemProperties properties = ItemProperties.builder(Material.DIAMOND_SWORD)
      .customModelDataFloats(1.5f, 2.0f)
      .customModelDataFlags(true, false)
      .customModelDataStrings("hilt", "blade")
      .customModelDataColors(Color.RED)
      .build();
    assertEquals(List.of(1.5f, 2.0f), properties.customModelDataFloats());
    assertEquals(List.of(true, false), properties.customModelDataFlags());
    assertEquals(List.of("hilt", "blade"), properties.customModelDataStrings());
    assertEquals(List.of(Color.RED), properties.customModelDataColors());
    assertThrows(
      UnsupportedOperationException.class,
      () -> properties.customModelDataFloats().add(9.0f)
    );
  }
}
