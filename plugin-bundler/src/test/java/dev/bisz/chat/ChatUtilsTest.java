package dev.bisz.chat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChatUtilsTest {

  @Test
  void convertsRomanNumeralsWithinTheSupportedRange() {
    assertEquals("MCMXCIV", ChatUtils.intToRoman(1994));
    assertThrows(IllegalArgumentException.class, () -> ChatUtils.intToRoman(0));
    assertThrows(IllegalArgumentException.class, () ->
      ChatUtils.intToRoman(4000)
    );
  }

  @Test
  void wrapsTextUsingVisibleCharacters() {
    assertEquals("one two\nthree", ChatUtils.wrapText("one two three", 7));
    assertEquals(
      "§aone two\n§athree",
      ChatUtils.wrapTextColor("§aone two three", 7)
    );
  }

  @Test
  void rendersCountedItemsInTheUniversalFormat() {
    assertEquals("§7x1 §fDiamond", ChatUtils.countedItem(1, "§fDiamond"));
    assertEquals(
      "§7x64 §bEnchanted Book",
      ChatUtils.countedItem(64, "§bEnchanted Book")
    );
  }

  @Test
  void retainsLegacyListAndTimeHelpersWithoutTheOldObjectArrayBug() {
    assertEquals(List.of("alpha", "2"), ChatUtils.fromObjects("ALPHA", 2));
    assertEquals(
      List.of("third", "second", "first"),
      ChatUtils.reversedFromArgs("first", "second", "third")
    );
    assertArrayEquals(
      new long[] { 1, 2, 3, 4 },
      ChatUtils.convertMillisToTime(93_784_000L)
    );
  }
}
