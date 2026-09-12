/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.items;

public final class RomanNumerals {

  private RomanNumerals() {}

  public static String format(int value) {
    if (value < 1 || value > 3999) {
      throw new IllegalArgumentException("Roman numerals support 1..3999");
    }
    int[] values = new int[] {
      1000,
      900,
      500,
      400,
      100,
      90,
      50,
      40,
      10,
      9,
      5,
      4,
      1,
    };
    String[] symbols = new String[] {
      "M",
      "CM",
      "D",
      "CD",
      "C",
      "XC",
      "L",
      "XL",
      "X",
      "IX",
      "V",
      "IV",
      "I",
    };
    StringBuilder result = new StringBuilder();
    for (int index = 0; index < values.length; ++index) {
      while (value >= values[index]) {
        result.append(symbols[index]);
        value -= values[index];
      }
    }
    return result.toString();
  }
}
