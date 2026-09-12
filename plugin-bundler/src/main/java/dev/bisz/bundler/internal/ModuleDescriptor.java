/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.bundler.internal;

import dev.bisz.bundler.internal.ModuleState;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

public record ModuleDescriptor(
  String id,
  Set<String> dependencies,
  ModuleState state,
  String detail
) {
  public ModuleDescriptor {
    id = Objects.requireNonNull(id, "id");
    dependencies = Set.copyOf(
      (Collection) Objects.requireNonNull(dependencies, "dependencies")
    );
    state = Objects.requireNonNull(state, "state");
    detail = detail == null ? "" : detail;
  }
}
