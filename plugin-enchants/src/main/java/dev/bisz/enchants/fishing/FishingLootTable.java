package dev.bisz.enchants.fishing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

/** Weighted fish/treasure loot tables loaded from {@code fishing.yml}. */
final class FishingLootTable {
  private int baseTime = 15;
  private double efficiencyReduction = .15D;
  private double treasureChance = .05D;
  private final List<LootEntry> fish = new ArrayList<>();
  private final List<LootEntry> treasure = new ArrayList<>();
  private final Random random = new Random();

  void load(FileConfiguration config) {
    baseTime = config.getInt("base-time", 15);
    efficiencyReduction = config.getDouble("efficiency-reduction", .15D);
    treasureChance = config.getDouble("treasure-chance", .05D);
    fish.clear();
    for (Map<?, ?> entry : config.getMapList("loot.fish")) {
      LootEntry parsed = LootEntry.parse(entry);
      if (parsed != null) fish.add(parsed);
    }
    treasure.clear();
    for (Map<?, ?> entry : config.getMapList("loot.treasure")) {
      LootEntry parsed = LootEntry.parse(entry);
      if (parsed != null) treasure.add(parsed);
    }
  }

  int baseTime() { return baseTime; }
  double efficiencyReduction() { return efficiencyReduction; }

  ItemStack roll(boolean silkTouch, int fortune) {
    if (silkTouch || random.nextDouble() < treasureChance) return roll(treasure, fortune);
    return roll(fish, fortune);
  }

  private ItemStack roll(List<LootEntry> pool, int fortune) {
    if (pool.isEmpty()) return new ItemStack(Material.COD);
    int total = pool.stream().mapToInt(entry -> entry.weight).sum();
    int roll = random.nextInt(Math.max(1, total));
    int accumulated = 0;
    for (LootEntry entry : pool) {
      accumulated += entry.weight;
      if (roll < accumulated) {
        int amount = entry.min + (entry.max > entry.min ? random.nextInt(entry.max - entry.min + 1) : 0);
        if (fortune > 0) amount = Math.min(64, amount * fortune);
        return new ItemStack(entry.material, Math.max(1, amount));
      }
    }
    return new ItemStack(pool.get(pool.size() - 1).material, 1);
  }

  private static final class LootEntry {
    private final Material material;
    private final int weight;
    private final int min;
    private final int max;

    private LootEntry(Material material, int weight, int min, int max) {
      this.material = material;
      this.weight = weight;
      this.min = min;
      this.max = max;
    }

    static LootEntry parse(Map<?, ?> map) {
      Object materialValue = map.get("material");
      if (materialValue == null) return null;
      Material material = Material.matchMaterial(String.valueOf(materialValue));
      if (material == null) return null;
      return new LootEntry(material, number(map.get("weight"), 1), number(map.get("min"), 1), number(map.get("max"), 1));
    }

    private static int number(Object value, int fallback) {
      return value instanceof Number number ? number.intValue() : fallback;
    }
  }
}
