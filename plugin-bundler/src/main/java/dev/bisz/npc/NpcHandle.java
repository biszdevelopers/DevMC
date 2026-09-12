/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.npc;

import java.util.Objects;
import java.util.function.*;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class NpcHandle {

  private final int id;
  private final String key;
  private final Supplier<Entity> entity;
  private final Runnable destroy;
  private final Consumer<Location> teleport;
  private final EquipmentUpdater equipment;
  private final NpcNavigator navigator;

  public NpcHandle(
    int id,
    String key,
    Supplier<Entity> entity,
    Runnable destroy,
    Consumer<Location> teleport,
    EquipmentUpdater equipment,
    NpcNavigator navigator
  ) {
    this.id = id;
    this.key = Objects.requireNonNull(key);
    this.entity = entity;
    this.destroy = destroy;
    this.teleport = teleport;
    this.equipment = equipment;
    this.navigator = navigator;
  }

  public int id() {
    return id;
  }

  public String key() {
    return key;
  }

  public Entity entity() {
    return entity.get();
  }

  public void destroy() {
    destroy.run();
  }

  public void teleport(Location l) {
    teleport.accept(l.clone());
  }

  public void equipment(EquipmentSlot s, ItemStack i) {
    equipment.set(s, i == null ? null : i.clone());
  }

  public NpcNavigator navigator() {
    return navigator;
  }

  @FunctionalInterface
  public interface EquipmentUpdater {
    void set(EquipmentSlot slot, ItemStack item);
  }
}
