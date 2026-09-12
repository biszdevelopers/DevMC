package dev.bisz.combat.commands;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

/** Resolves the player responsible for Bukkit and Mohist-backed damage entities. */
final class PlayerDamageSourceResolver {

  private static final int MAX_OWNER_DEPTH = 6;
  private static final String[] OWNER_ACCESSORS = {
    "getOwner",
    "getShooter",
    "getSource",
  };
  private static final String[] NATIVE_OWNER_ACCESSORS = {
    "m_19749_",
    "func_234616_v_",
  };
  private static final String[] OWNER_FIELDS = {
    "owner",
    "cachedOwner",
    "f_150163_",
    "ownerUUID",
    "f_37244_",
  };

  private PlayerDamageSourceResolver() {}

  static Player resolve(Entity damager) {
    Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    return resolveValue(damager, visited, 0);
  }

  private static Player resolveValue(
    Object value,
    Set<Object> visited,
    int depth
  ) {
    if (value == null || depth > MAX_OWNER_DEPTH || !visited.add(value)) {
      return null;
    }
    if (value instanceof Player) {
      return (Player) value;
    }
    if (value instanceof UUID) {
      return Bukkit.getPlayer((UUID) value);
    }

    // Bukkit projectiles cover arrows, tridents, potions, fireworks, and
    // custom projectiles for which Mohist supplies a Bukkit projectile wrapper.
    if (value instanceof Projectile) {
      Player shooter = resolveValue(
        getBukkitShooter((Projectile) value),
        visited,
        depth + 1
      );
      if (shooter != null) {
        return shooter;
      }
    }

    // Some Bukkit entities (for example primed TNT or area-effect clouds)
    // and modded wrappers expose their attribution through one of these.
    for (String accessor : OWNER_ACCESSORS) {
      Player owner = resolveValue(
        invokeNoArg(value, accessor),
        visited,
        depth + 1
      );
      if (owner != null) {
        return owner;
      }
    }

    // Forge's production runtime uses SRG/obfuscated method names. In
    // Minecraft 1.20.1, Projectile#getOwner() is m_19749_(). The legacy
    // name keeps the fallback useful on older hybrid server mappings.
    for (String accessor : NATIVE_OWNER_ACCESSORS) {
      Player owner = resolveValue(
        invokeNoArg(value, accessor),
        visited,
        depth + 1
      );
      if (owner != null) {
        return owner;
      }
    }

    // Field lookup covers hybrid runtimes that remap neither the method nor
    // its readable name. TACZ's native projectile inherits f_150163_ (the
    // cached owner entity) and f_37244_ (the persisted owner UUID).
    for (String fieldName : OWNER_FIELDS) {
      Player owner = resolveValue(
        readField(value, fieldName),
        visited,
        depth + 1
      );
      if (owner != null) {
        return owner;
      }
    }

    // CraftCustomEntity does not necessarily implement Bukkit Projectile.
    // Unwrap it and inspect the native Minecraft/Forge entity. TACZ's
    // EntityKineticBullet extends the native Projectile class and getOwner()
    // returns the player that fired it.
    Player nativeOwner = resolveValue(
      invokeNoArg(value, "getHandle"),
      visited,
      depth + 1
    );
    if (nativeOwner != null) {
      return nativeOwner;
    }

    // Native ServerPlayer and similar owner objects can be converted back
    // to their Bukkit entity without linking against CraftBukkit or Mohist.
    return resolveValue(
      invokeNoArg(value, "getBukkitEntity"),
      visited,
      depth + 1
    );
  }

  private static Object getBukkitShooter(Projectile projectile) {
    try {
      return projectile.getShooter();
    } catch (RuntimeException | LinkageError ignored) {
      return null;
    }
  }

  private static Object invokeNoArg(Object target, String methodName) {
    try {
      Method method = target.getClass().getMethod(methodName);
      if (method.getParameterCount() != 0) {
        return null;
      }
      return method.invoke(target);
    } catch (
      ReflectiveOperationException
      | RuntimeException
      | LinkageError ignored
    ) {
      return null;
    }
  }

  private static Object readField(Object target, String fieldName) {
    for (
      Class<?> type = target.getClass();
      type != null;
      type = type.getSuperclass()
    ) {
      try {
        Field field = type.getDeclaredField(fieldName);
        if (!field.trySetAccessible()) {
          return null;
        }
        return field.get(target);
      } catch (NoSuchFieldException ignored) {
        // The owner fields are commonly inherited, so continue upward.
      } catch (
        IllegalAccessException
        | RuntimeException
        | LinkageError ignored
      ) {
        return null;
      }
    }
    return null;
  }
}
