/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.AsyncPlayerPreLoginEvent
 *  org.bukkit.event.player.AsyncPlayerPreLoginEvent$Result
 *  org.bukkit.plugin.Plugin
 */
package dev.bisz.players.web;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.bundler.internal.BundlerModule;
import dev.bisz.bundler.internal.ModuleContext;
import dev.bisz.players.web.AuthenticationDecision;
import dev.bisz.players.web.AuthenticationGateway;
import dev.bisz.players.web.LoginAttempt;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.Plugin;

public final class AuthenticationModule implements BundlerModule, Listener {

  private long timeoutMillis;

  @Override
  public String id() {
    return "authentication";
  }

  @Override
  public Set<String> dependencies() {
    return Set.of("locales");
  }

  @Override
  public void start(ModuleContext context) {
    this.timeoutMillis = context
      .plugin()
      .getConfig()
      .getLong("authentication.timeout-millis", 5000L);
    Bukkit.getPluginManager().registerEvents(
      (Listener) this,
      (Plugin) context.plugin()
    );
  }

  @Override
  public void stop() {}

  @EventHandler
  public void onPreLogin(AsyncPlayerPreLoginEvent event) {
    AuthenticationGateway gateway =
      BundlerPlugin.instance().authenticationGateway();
    AuthenticationDecision decision = AuthenticationDecision.UNAVAILABLE;
    if (gateway != null) {
      try {
        decision = gateway
          .verify(
            new LoginAttempt(
              event.getUniqueId(),
              event.getName(),
              event.getAddress()
            )
          )
          .toCompletableFuture()
          .get(this.timeoutMillis, TimeUnit.MILLISECONDS);
      } catch (Exception ignored) {
        decision = AuthenticationDecision.UNAVAILABLE;
      }
    }
    if (decision != AuthenticationDecision.VERIFIED) {
      event.disallow(
        AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
        "Authentication is enabled but no verified external authentication signal was received."
      );
    }
  }
}
