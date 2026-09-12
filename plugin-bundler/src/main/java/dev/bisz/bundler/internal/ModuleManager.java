/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.bundler.internal;

import dev.bisz.bundler.internal.BundlerModule;
import dev.bisz.bundler.internal.ModuleContext;
import dev.bisz.bundler.internal.ModuleDescriptor;
import dev.bisz.bundler.internal.ModuleState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModuleManager {

  private final Map<String, BundlerModule> modules = new LinkedHashMap<
    String,
    BundlerModule
  >();
  private final Map<String, ModuleDescriptor> descriptors = new LinkedHashMap<
    String,
    ModuleDescriptor
  >();
  private final List<BundlerModule> started = new ArrayList<BundlerModule>();

  public void add(BundlerModule module) {
    if (this.modules.putIfAbsent(module.id(), module) != null) {
      throw new IllegalArgumentException("Duplicate module id: " + module.id());
    }
    this.descriptors.put(
      module.id(),
      new ModuleDescriptor(
        module.id(),
        module.dependencies(),
        ModuleState.DISABLED,
        ""
      )
    );
  }

  public void startEnabled(Set<String> enabled, ModuleContext context) {
    for (BundlerModule module : this.modules.values()) {
      if (!enabled.contains(module.id())) continue;
      this.start(module, enabled, context, new ArrayList<String>());
    }
  }

  private void start(
    BundlerModule module,
    Set<String> enabled,
    ModuleContext context,
    List<String> stack
  ) {
    ModuleDescriptor existing = this.descriptors.get(module.id());
    if (
      existing.state() == ModuleState.ACTIVE ||
      existing.state() == ModuleState.UNAVAILABLE
    ) {
      return;
    }
    if (stack.contains(module.id())) {
      this.mark(
        module,
        ModuleState.UNAVAILABLE,
        "Circular module dependency: " + String.valueOf(stack)
      );
      return;
    }
    stack.add(module.id());
    for (String dependency : module.dependencies()) {
      BundlerModule required = this.modules.get(dependency);
      if (required == null || !enabled.contains(dependency)) {
        this.mark(
          module,
          ModuleState.UNAVAILABLE,
          "Required module is disabled: " + dependency
        );
        stack.remove(stack.size() - 1);
        return;
      }
      this.start(required, enabled, context, stack);
      if (
        this.descriptors.get(dependency).state() == ModuleState.ACTIVE
      ) continue;
      this.mark(
        module,
        ModuleState.UNAVAILABLE,
        "Required module is unavailable: " + dependency
      );
      stack.remove(stack.size() - 1);
      return;
    }
    this.mark(module, ModuleState.STARTING, "");
    try {
      module.start(context);
      this.started.add(module);
      this.mark(module, ModuleState.ACTIVE, "");
    } catch (Exception exception) {
      context
        .plugin()
        .getLogger()
        .severe(
          "Module " + module.id() + " is unavailable: " + exception.getMessage()
        );
      this.mark(
        module,
        ModuleState.UNAVAILABLE,
        exception.getClass().getSimpleName() + ": " + exception.getMessage()
      );
    }
    stack.remove(stack.size() - 1);
  }

  public void stopAll() {
    for (int index = this.started.size() - 1; index >= 0; --index) {
      BundlerModule module = this.started.get(index);
      try {
        module.stop();
      } catch (Exception exception) {
        // empty catch block
      }
      this.mark(module, ModuleState.STOPPED, "");
    }
    this.started.clear();
  }

  public Collection<ModuleDescriptor> descriptors() {
    return this.descriptors.values()
      .stream()
      .sorted(Comparator.comparing(ModuleDescriptor::id))
      .toList();
  }

  private void mark(BundlerModule module, ModuleState state, String detail) {
    this.descriptors.put(
      module.id(),
      new ModuleDescriptor(module.id(), module.dependencies(), state, detail)
    );
  }
}
