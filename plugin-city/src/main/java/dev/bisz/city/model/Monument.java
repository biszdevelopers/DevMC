package dev.bisz.city.model;

import dev.bisz.city.storage.Json;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Location;

/** A persistent, contested landmark in the wilderness. */
public final class Monument {

  private final String id;
  private final String world;
  private final int minX;
  private final int minY;
  private final int minZ;
  private final int maxX;
  private final int maxY;
  private final int maxZ;
  private int tier;
  private String lootTableId;
  private long respawnTicks;
  private long lastLootedAt;
  private double hazard;
  private String keycard;

  public Monument(
    String id,
    String world,
    int minX,
    int minY,
    int minZ,
    int maxX,
    int maxY,
    int maxZ,
    int tier,
    String lootTableId,
    long respawnTicks,
    double hazard,
    String keycard
  ) {
    this.id = Objects.requireNonNull(id, "id");
    this.world = Objects.requireNonNull(world, "world");
    this.minX = Math.min(minX, maxX);
    this.minY = Math.min(minY, maxY);
    this.minZ = Math.min(minZ, maxZ);
    this.maxX = Math.max(minX, maxX);
    this.maxY = Math.max(minY, maxY);
    this.maxZ = Math.max(minZ, maxZ);
    this.tier = tier;
    this.lootTableId = Objects.requireNonNull(lootTableId, "lootTableId");
    this.respawnTicks = Math.max(1L, respawnTicks);
    this.hazard = Math.max(0.0, hazard);
    this.keycard = keycard;
  }

  public String id() {
    return id;
  }

  public String world() {
    return world;
  }

  public int minX() {
    return minX;
  }

  public int minY() {
    return minY;
  }

  public int minZ() {
    return minZ;
  }

  public int maxX() {
    return maxX;
  }

  public int maxY() {
    return maxY;
  }

  public int maxZ() {
    return maxZ;
  }

  public int tier() {
    return tier;
  }

  public void tier(int value) {
    this.tier = value;
  }

  public String lootTableId() {
    return lootTableId;
  }

  public void lootTableId(String value) {
    this.lootTableId = Objects.requireNonNull(value, "lootTableId");
  }

  public long respawnTicks() {
    return respawnTicks;
  }

  public void respawnTicks(long value) {
    this.respawnTicks = Math.max(1L, value);
  }

  public long lastLootedAt() {
    return lastLootedAt;
  }

  public void lastLootedAt(long value) {
    this.lastLootedAt = value;
  }

  public double hazard() {
    return hazard;
  }

  public void hazard(double value) {
    this.hazard = Math.max(0.0, value);
  }

  public String keycard() {
    return keycard;
  }

  public void keycard(String value) {
    this.keycard = value;
  }

  /** Whether a location falls inside this monument's bounds. */
  public boolean contains(Location location) {
    if (location == null || location.getWorld() == null) return false;
    if (!location.getWorld().getName().equals(world)) return false;
    int x = location.getBlockX();
    int y = location.getBlockY();
    int z = location.getBlockZ();
    return (
      x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ
    );
  }

  /** Serializes this monument into a JSON-compatible map. */
  public Map<String, Object> toMap() {
    java.util.LinkedHashMap<String, Object> map =
      new java.util.LinkedHashMap<>();
    map.put("id", id);
    map.put("world", world);
    map.put("min_x", minX);
    map.put("min_y", minY);
    map.put("min_z", minZ);
    map.put("max_x", maxX);
    map.put("max_y", maxY);
    map.put("max_z", maxZ);
    map.put("tier", tier);
    map.put("loot_table", lootTableId);
    map.put("respawn_ticks", respawnTicks);
    map.put("last_looted_at", lastLootedAt);
    map.put("hazard", hazard);
    map.put("keycard", keycard);
    return map;
  }

  /** Restores a monument from its stored representation. */
  public static Monument fromMap(Map<String, Object> map) {
    String id = Json.string(map, "id", null);
    String world = Json.string(map, "world", null);
    if (id == null || world == null) {
      throw new IllegalArgumentException("Monument entry is missing id or world");
    }
    Monument monument = new Monument(
      id,
      world,
      Json.integer(map, "min_x", 0),
      Json.integer(map, "min_y", 0),
      Json.integer(map, "min_z", 0),
      Json.integer(map, "max_x", 0),
      Json.integer(map, "max_y", 0),
      Json.integer(map, "max_z", 0),
      Json.integer(map, "tier", 1),
      Json.string(map, "loot_table", "tier1"),
      Json.longValue(map, "respawn_ticks", 72_000L),
      Json.decimal(map, "hazard", 0.0),
      Json.string(map, "keycard", null)
    );
    monument.lastLootedAt = Json.longValue(map, "last_looted_at", 0L);
    return monument;
  }
}
