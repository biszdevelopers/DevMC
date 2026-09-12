/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.bundler.internal;

import dev.bisz.bundler.internal.ModuleContext;
import java.util.Set;

public interface BundlerModule {
  public String id();

  public Set<String> dependencies();

  public void start(ModuleContext var1) throws Exception;

  public void stop() throws Exception;
}
