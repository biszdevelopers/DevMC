/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.npc;

import dev.bisz.npc.NpcHandle;
import java.util.Collection;
import java.util.Optional;

public interface NpcService {
  NpcHandle create(NpcDefinition definition);
  Optional<NpcHandle> find(int id);
  Optional<NpcHandle> find(String applicationKey);
  Collection<NpcHandle> all();
  void destroy(String applicationKey);
}
