/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.items;

import java.util.concurrent.ThreadLocalRandom;

public final class RNGMath {

  private RNGMath() {}

  public static int between(int minimum, int maximum) {
    if (minimum > maximum) {
      throw new IllegalArgumentException("minimum must not exceed maximum");
    }
    return ThreadLocalRandom.current().nextInt(minimum, maximum + 1);
  }

  public static boolean chance(double probability) {
    if (probability < 0.0 || probability > 1.0 || Double.isNaN(probability)) {
      throw new IllegalArgumentException("probability must be 0..1");
    }
    return ThreadLocalRandom.current().nextDouble() < probability;
  }
}
