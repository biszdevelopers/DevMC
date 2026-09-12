/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.players.moderation.internal;

import dev.bisz.bundler.internal.BundlerModule;
import dev.bisz.bundler.internal.ModuleContext;
import dev.bisz.players.moderation.ModerationService;
import dev.bisz.players.moderation.internal.JsonModerationService;
import java.util.Set;

public final class ModerationModule implements BundlerModule {

  @Override
  public String id() {
    return "moderation";
  }

  @Override
  public Set<String> dependencies() {
    return Set.of();
  }

  @Override
  public void start(ModuleContext context) {
    context
      .services()
      .register(
        ModerationService.class,
        new JsonModerationService(context.database())
      );
  }

  @Override
  public void stop() {}
}
