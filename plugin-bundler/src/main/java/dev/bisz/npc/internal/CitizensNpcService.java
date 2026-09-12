/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.npc.internal;

import dev.bisz.npc.*;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;

public final class CitizensNpcService implements NpcService {

  private final Map<String, NpcHandle> byKey = new LinkedHashMap<>();
  private final Map<Integer, NpcHandle> byId = new LinkedHashMap<>();

  @Override
  public synchronized NpcHandle create(NpcDefinition d) {
    destroy(d.key());
    try {
      Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
      Object registry = api.getMethod("getNPCRegistry").invoke(null);
      Object npc = registry
        .getClass()
        .getMethod("createNPC", EntityType.class, String.class)
        .invoke(registry, d.type(), d.name());
      configureNameplate(npc, d.nameplateVisible());
      int id = ((Number) npc
          .getClass()
          .getMethod("getId")
          .invoke(npc)).intValue();
      npc
        .getClass()
        .getMethod("spawn", Location.class)
        .invoke(npc, d.location());
      Entity entity = (Entity) npc
        .getClass()
        .getMethod("getEntity")
        .invoke(npc);
      configure(entity, d);
      if (d.pose() == NpcPose.SLEEPING) sleep(npc, d.location());
      NpcHandle h = new NpcHandle(
        id,
        d.key(),
        () -> entity,
        () -> deregister(registry, npc),
        entity::teleport,
        (s, i) -> equip(entity, s, i),
        navigator(npc)
      );
      byKey.put(d.key(), h);
      byId.put(id, h);
      return h;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Citizens NPC creation failed", e);
    }
  }

  private void sleep(Object npc, Location location) {
    try {
      Class<?> trait = Class.forName("net.citizensnpcs.trait.SleepTrait");
      Object value = npc
        .getClass()
        .getMethod("getOrAddTrait", Class.class)
        .invoke(npc, trait);
      trait.getMethod("setSleeping", Location.class).invoke(value, location);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
        "Citizens sleeping pose is unavailable",
        e
      );
    }
  }

  private void configure(Entity e, NpcDefinition d) {
    e.setCustomNameVisible(d.nameplateVisible());
    e.setInvulnerable(d.protectedNpc());
    if (e instanceof LivingEntity l) {
      try {
        l.setCollidable(d.collidable());
      } catch (Throwable ignored) {}
      try {
        l.setAI(false);
      } catch (Throwable ignored) {}
      d.equipment().forEach((s, i) -> equip(e, s, i));
      if (d.pose() == NpcPose.SLEEPING) try {
        e.getClass().getMethod("setPose", Pose.class).invoke(e, Pose.SLEEPING);
      } catch (Throwable ignored) {}
    }
  }

  private void configureNameplate(Object npc, boolean visible) {
    try {
      Object data = npc.getClass().getMethod("data").invoke(npc);
      String key = "nameplate-visible";
      try {
        Class<?> metadata = Class.forName(
          "net.citizensnpcs.api.npc.NPC$Metadata"
        );
        Object nameplate = metadata.getField("NAMEPLATE_VISIBLE").get(null);
        key = String.valueOf(metadata.getMethod("getKey").invoke(nameplate));
      } catch (ReflectiveOperationException ignored) {}
      data
        .getClass()
        .getMethod("setPersistent", String.class, Object.class)
        .invoke(data, key, visible);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
        "Citizens nameplate configuration failed",
        e
      );
    }
  }

  private void equip(Entity e, EquipmentSlot s, ItemStack i) {
    if (!(e instanceof LivingEntity l) || l.getEquipment() == null) return;
    switch (s) {
      case HEAD -> l.getEquipment().setHelmet(i);
      case CHEST -> l.getEquipment().setChestplate(i);
      case LEGS -> l.getEquipment().setLeggings(i);
      case FEET -> l.getEquipment().setBoots(i);
      case HAND -> l.getEquipment().setItemInMainHand(i);
      case OFF_HAND -> l.getEquipment().setItemInOffHand(i);
      default -> {}
    }
  }

  private NpcNavigator navigator(Object npc) {
    return new NpcNavigator() {
      private Object nav() {
        try {
          return npc.getClass().getMethod("getNavigator").invoke(npc);
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      }

      public void navigateTo(Location t) {
        try {
          Object n = nav();
          n.getClass().getMethod("setTarget", Location.class).invoke(n, t);
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      }

      public void cancel() {
        try {
          Object n = nav();
          n.getClass().getMethod("cancelNavigation").invoke(n);
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      }

      public boolean navigating() {
        try {
          Object n = nav();
          return (Boolean) n.getClass().getMethod("isNavigating").invoke(n);
        } catch (Exception e) {
          return false;
        }
      }
    };
  }

  private void deregister(Object registry, Object npc) {
    int id = id(npc);
    try {
      registry
        .getClass()
        .getMethod("deregister", Class.forName("net.citizensnpcs.api.npc.NPC"))
        .invoke(registry, npc);
    } catch (Exception ignored) {}
    synchronized (this) {
      byKey.values().removeIf(h -> h.id() == id);
      byId.remove(id);
    }
  }

  private int id(Object npc) {
    try {
      return (
        (Number) npc.getClass().getMethod("getId").invoke(npc)
      ).intValue();
    } catch (Exception e) {
      return -1;
    }
  }

  @Override
  public synchronized Optional<NpcHandle> find(int id) {
    return Optional.ofNullable(byId.get(id));
  }

  @Override
  public synchronized Optional<NpcHandle> find(String k) {
    return Optional.ofNullable(byKey.get(k));
  }

  @Override
  public synchronized Collection<NpcHandle> all() {
    return List.copyOf(byKey.values());
  }

  @Override
  public synchronized void destroy(String k) {
    NpcHandle h = byKey.get(k);
    if (h != null) h.destroy();
  }

  public synchronized void dispose() {
    for (NpcHandle h : new ArrayList<>(byKey.values())) h.destroy();
  }
}
