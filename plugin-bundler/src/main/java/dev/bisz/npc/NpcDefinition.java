package dev.bisz.npc;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class NpcDefinition {

  private final String key, name;
  private final EntityType type;
  private final Location location;
  private final NpcPose pose;
  private final boolean protectedNpc, collidable, nameplateVisible;
  private final Map<EquipmentSlot, ItemStack> equipment;

  private NpcDefinition(Builder b) {
    key = b.key;
    type = b.type;
    name = b.name;
    location = b.location.clone();
    pose = b.pose;
    protectedNpc = b.protectedNpc;
    collidable = b.collidable;
    nameplateVisible = b.nameplateVisible;
    equipment = Map.copyOf(b.equipment);
  }

  public static Builder builder(
    String key,
    EntityType type,
    Location location
  ) {
    return new Builder(key, type, location);
  }

  public String key() {
    return key;
  }

  public EntityType type() {
    return type;
  }

  public String name() {
    return name;
  }

  public Location location() {
    return location.clone();
  }

  public NpcPose pose() {
    return pose;
  }

  public boolean protectedNpc() {
    return protectedNpc;
  }

  public boolean collidable() {
    return collidable;
  }

  public boolean nameplateVisible() {
    return nameplateVisible;
  }

  public Map<EquipmentSlot, ItemStack> equipment() {
    return equipment;
  }

  public static final class Builder {

    private final String key;
    private final EntityType type;
    private final Location location;
    private String name = "NPC";
    private NpcPose pose = NpcPose.STANDING;
    private boolean protectedNpc = true,
      collidable,
      nameplateVisible = true;
    private final Map<EquipmentSlot, ItemStack> equipment =
      new LinkedHashMap<>();

    private Builder(String k, EntityType t, Location l) {
      key = Objects.requireNonNull(k);
      type = Objects.requireNonNull(t);
      location = Objects.requireNonNull(l);
    }

    public Builder name(String v) {
      name = Objects.requireNonNull(v);
      return this;
    }

    public Builder pose(NpcPose v) {
      pose = Objects.requireNonNull(v);
      return this;
    }

    public Builder protectedNpc(boolean v) {
      protectedNpc = v;
      return this;
    }

    public Builder collidable(boolean v) {
      collidable = v;
      return this;
    }

    public Builder nameplateVisible(boolean v) {
      nameplateVisible = v;
      return this;
    }

    public Builder equipment(EquipmentSlot s, ItemStack i) {
      if (i == null) equipment.remove(s);
      else equipment.put(s, i.clone());
      return this;
    }

    public NpcDefinition build() {
      return new NpcDefinition(this);
    }
  }
}
