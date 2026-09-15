package dev.bisz.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

class DevEnchantCommandTest {
  @Test void metadataArgumentsParseBooleansAndWholeNumbers() {
    Map<NamespacedKey, Object> metadata = DevEnchantCommand.parseMetadata(new String[] {
      "minecraft:mending", "1", "enchants:mend_count=56_000", "enchants:mend_remainder=3",
      "enchants:flag=true", "enchants:other=FALSE"
    }, 2);
    assertEquals(4, metadata.size());
    assertEquals(56_000, metadata.get(NamespacedKey.fromString("enchants:mend_count")));
    assertEquals(3, metadata.get(NamespacedKey.fromString("enchants:mend_remainder")));
    assertEquals(Boolean.TRUE, metadata.get(NamespacedKey.fromString("enchants:flag")));
    assertEquals(Boolean.FALSE, metadata.get(NamespacedKey.fromString("enchants:other")));
  }

  @Test void metadataIsOptionalAndLaterDuplicatesWin() {
    assertEquals(Map.of(), DevEnchantCommand.parseMetadata(new String[] {"enchants:winged", "1"}, 2));
    Map<NamespacedKey, Object> metadata = DevEnchantCommand.parseMetadata(
      new String[] {"enchants:winged", "1", "enchants:key=1", "enchants:key=2"}, 2);
    assertEquals(Map.of(NamespacedKey.fromString("enchants:key"), 2), metadata);
  }

  @Test void metadataValuesAllowUnderscoresAndNegativeWholeNumbers() {
    assertEquals(56_000, DevEnchantCommand.parseMetadataValue("56_000"));
    assertEquals(-5, DevEnchantCommand.parseMetadataValue("-5"));
    assertThrows(IllegalArgumentException.class, () -> DevEnchantCommand.parseMetadataValue("trueish"));
    assertThrows(IllegalArgumentException.class, () -> DevEnchantCommand.parseMetadataValue("1.5"));
  }

  @Test void invalidMetadataArgumentsAreRejected() {
    for (String token : new String[] {
      "mend_count=5", "enchants:mend_count", "enchants:mend_count=", "=5",
      "enchants:key=text", "enchants:key=1.5", ":key=1"
    }) {
      assertThrows(IllegalArgumentException.class,
        () -> DevEnchantCommand.parseMetadata(new String[] {"minecraft:mending", "1", token}, 2), token);
    }
  }
}
