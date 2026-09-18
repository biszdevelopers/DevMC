package dev.bisz.world.model;

import dev.bisz.world.storage.Json;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** A settlement: an irregular set of chunks with its own economy and flags. */
public final class Settlement {

  private final String id;
  private String name;
  private final String world;
  private final Set<ChunkKey> chunks = new LinkedHashSet<>();
  private String spawnWorld;
  private double spawnX;
  private double spawnY;
  private double spawnZ;
  private float spawnYaw;
  private float spawnPitch;
  private long rentPrice;
  private long rentPeriodMillis;
  private int plotSize;
  private boolean pvpAllowed;
  private boolean policeEnabled;
  private long treasury;

  public Settlement(
    String id,
    String name,
    String world,
    long rentPrice,
    long rentPeriodMillis,
    int plotSize
  ) {
    this.id = Objects.requireNonNull(id, "id");
    this.name = Objects.requireNonNull(name, "name");
    this.world = Objects.requireNonNull(world, "world");
    this.rentPrice = Math.max(0L, rentPrice);
    this.rentPeriodMillis = Math.max(1L, rentPeriodMillis);
    this.plotSize = normalizePlotSize(plotSize);
    this.spawnWorld = world;
    this.policeEnabled = true;
    this.pvpAllowed = false;
  }

  /** The largest plot size that still tiles a 16-block chunk. */
  public static int normalizePlotSize(int requested) {
    int value = Math.max(1, requested);
    if (value > 16) return 16;
    if (Integer.bitCount(value) != 1) {
      int power = 1;
      while (power * 2 <= value) power *= 2;
      return power;
    }
    return value;
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public void name(String value) {
    this.name = Objects.requireNonNull(value, "name");
  }

  public String world() {
    return world;
  }

  public Set<ChunkKey> chunks() {
    return chunks;
  }

  public boolean contains(ChunkKey key) {
    return chunks.contains(key);
  }

  public Location spawn() {
    World bukkitWorld = Bukkit.getWorld(spawnWorld);
    if (bukkitWorld == null) return null;
    return new Location(
      bukkitWorld,
      spawnX,
      spawnY,
      spawnZ,
      spawnYaw,
      spawnPitch
    );
  }

  public void spawn(Location location) {
    Objects.requireNonNull(location, "location");
    World bukkitWorld = Objects.requireNonNull(
      location.getWorld(),
      "location world"
    );
    this.spawnWorld = bukkitWorld.getName();
    this.spawnX = location.getX();
    this.spawnY = location.getY();
    this.spawnZ = location.getZ();
    this.spawnYaw = location.getYaw();
    this.spawnPitch = location.getPitch();
  }

  public long rentPrice() {
    return rentPrice;
  }

  public void rentPrice(long value) {
    this.rentPrice = Math.max(0L, value);
  }

  public long rentPeriodMillis() {
    return rentPeriodMillis;
  }

  public void rentPeriodMillis(long value) {
    this.rentPeriodMillis = Math.max(1L, value);
  }

  public int plotSize() {
    return plotSize;
  }

  public void plotSize(int value) {
    this.plotSize = normalizePlotSize(value);
  }

  public boolean pvpAllowed() {
    return pvpAllowed;
  }

  public void pvpAllowed(boolean value) {
    this.pvpAllowed = value;
  }

  public boolean policeEnabled() {
    return policeEnabled;
  }

  public void policeEnabled(boolean value) {
    this.policeEnabled = value;
  }

  public long treasury() {
    return treasury;
  }

  public void treasury(long value) {
    this.treasury = Math.max(0L, value);
  }

  /** Serializes this settlement into a JSON-compatible map. */
  public Map<String, Object> toMap() {
    return Map.ofEntries(
      Map.entry("id", id),
      Map.entry("name", name),
      Map.entry("world", world),
      Map.entry("chunks", List.copyOf(chunks.stream().map(ChunkKey::encode).toList())),
      Map.entry("spawn_world", spawnWorld),
      Map.entry("spawn_x", spawnX),
      Map.entry("spawn_y", spawnY),
      Map.entry("spawn_z", spawnZ),
      Map.entry("spawn_yaw", (double) spawnYaw),
      Map.entry("spawn_pitch", (double) spawnPitch),
      Map.entry("rent_price", rentPrice),
      Map.entry("rent_period_millis", rentPeriodMillis),
      Map.entry("plot_size", plotSize),
      Map.entry("pvp_allowed", pvpAllowed),
      Map.entry("police_enabled", policeEnabled),
      Map.entry("treasury", treasury)
    );
  }

  /** Restores a settlement from its stored representation. */
  public static Settlement fromMap(Map<String, Object> map) {
    String id = Json.string(map, "id", null);
    String world = Json.string(map, "world", null);
    if (id == null || world == null) {
      throw new IllegalArgumentException("Settlement entry is missing id or world");
    }
    Settlement settlement = new Settlement(
      id,
      Json.string(map, "name", id),
      world,
      Json.longValue(map, "rent_price", 0L),
      Json.longValue(map, "rent_period_millis", 86_400_000L),
      Json.integer(map, "plot_size", 16)
    );
    for (String encoded : Json.stringList(map, "chunks")) {
      try {
        settlement.chunks.add(ChunkKey.decode(encoded));
      } catch (IllegalArgumentException ignored) {
        // Skip malformed entries rather than failing the whole load.
      }
    }
    settlement.spawnWorld = Json.string(map, "spawn_world", world);
    settlement.spawnX = Json.decimal(map, "spawn_x", 0.0);
    settlement.spawnY = Json.decimal(map, "spawn_y", 64.0);
    settlement.spawnZ = Json.decimal(map, "spawn_z", 0.0);
    settlement.spawnYaw = (float) Json.decimal(map, "spawn_yaw", 0.0);
    settlement.spawnPitch = (float) Json.decimal(map, "spawn_pitch", 0.0);
    settlement.pvpAllowed = Json.bool(map, "pvp_allowed", false);
    settlement.policeEnabled = Json.bool(map, "police_enabled", true);
    settlement.treasury = Json.longValue(map, "treasury", 0L);
    return settlement;
  }
}
