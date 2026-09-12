/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.npc.internal;

import java.lang.reflect.Method;
import java.util.Optional;

final class CitizensNpcBridge {

  CitizensNpcBridge() {}

  Optional<Integer> find(int id) {
    try {
      Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
      Method registryMethod = api.getMethod("getNPCRegistry", new Class[0]);
      Object registry = registryMethod.invoke(null, new Object[0]);
      Object npc = registry
        .getClass()
        .getMethod("getById", Integer.TYPE)
        .invoke(registry, id);
      return npc == null ? Optional.empty() : Optional.of(id);
    } catch (ReflectiveOperationException exception) {
      return Optional.empty();
    }
  }
}
