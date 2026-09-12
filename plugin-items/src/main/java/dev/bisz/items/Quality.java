/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.items;

public enum Quality {
  COMMON("\u00a7f"),
  UNCOMMON("\u00a7e"),
  RARE("\u00a7b"),
  EPIC("\u00a7d"),
  LEGEND("\u00a7a"),
  RELIC("\u00a76"),
  HOLY("\u00a7c"),
  CONTRABAND("\u00a73");

  private final String colorCode;

  private Quality(String colorCode) {
    this.colorCode = colorCode;
  }

  public String colorCode() {
    return this.colorCode;
  }
}
