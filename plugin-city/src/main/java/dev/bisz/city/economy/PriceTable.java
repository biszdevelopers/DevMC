package dev.bisz.city.economy;

import dev.bisz.bundler.JSON;
import dev.bisz.city.storage.Json;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

/** The system vendor's item price list. The system only buys. */
public final class PriceTable {

  /** ServerData-relative price document. */
  public static final String FILE = "city/prices.json";

  private final Map<String, Long> prices;
  private final long fallback;

  private PriceTable(Map<String, Long> prices, long fallback) {
    this.prices = Map.copyOf(prices);
    this.fallback = Math.max(0L, fallback);
  }

  /** Installs defaults into ServerData and loads the price table. */
  public static PriceTable load(JavaPlugin plugin) {
    try (
      InputStream defaults = plugin.getResource("city/prices.json")
    ) {
      if (defaults == null) {
        throw new IllegalStateException(
          "Missing bundled city price defaults"
        );
      }
      JSON.mergeMissingDefaults(FILE, defaults);
    } catch (IOException exception) {
      throw new IllegalStateException(
        "Cannot close bundled city price defaults",
        exception
      );
    }
    Map<String, Object> stored = JSON.loadDataFromDataBase(FILE);
    Map<String, Long> prices = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : stored.entrySet()) {
      if (entry.getValue() instanceof Number number) {
        prices.put(entry.getKey(), number.longValue());
      }
    }
    long fallback = Json.longValue(stored, "default", 1L);
    return new PriceTable(prices, fallback);
  }

  /** Creates a price table from an in-memory map, for tests. */
  public static PriceTable from(Map<String, Object> values, long fallback) {
    Map<String, Long> prices = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      if (entry.getValue() instanceof Number number) {
        prices.put(entry.getKey(), number.longValue());
      }
    }
    return new PriceTable(prices, fallback);
  }

  /** The nits paid for a single unit of the supplied material. */
  public long unitPrice(Material material) {
    if (material == null || material.isAir()) return 0L;
    String key = material.getKey().getKey().toLowerCase(java.util.Locale.ROOT);
    Long exact = prices.get(key);
    if (exact != null) return Math.max(0L, exact);
    return fallback;
  }

  /** The total nits paid for a stack, ignoring contraband. */
  public long stackValue(Material material, int amount) {
    return unitPrice(material) * Math.max(0, amount);
  }
}
