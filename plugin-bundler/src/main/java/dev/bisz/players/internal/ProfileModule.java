/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Event
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package dev.bisz.players.internal;

import dev.bisz.bundler.internal.BundlerModule;
import dev.bisz.bundler.internal.ModuleContext;
import dev.bisz.players.ProfileService;
import dev.bisz.players.internal.JsonProfileService;
import dev.bisz.players.locales.PlayerLocaleUpdateEvent;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProfileModule implements BundlerModule, Listener {

  private ProfileService profiles;
  private JavaPlugin plugin;

  @Override
  public String id() {
    return "profiles";
  }

  @Override
  public Set<String> dependencies() {
    return Set.of();
  }

  @Override
  public void start(ModuleContext context) {
    this.plugin = context.plugin();
    this.profiles = new JsonProfileService(context.database());
    context.services().register(ProfileService.class, this.profiles);
    Bukkit.getPluginManager().registerEvents(
      (Listener) this,
      (Plugin) this.plugin
    );
    Bukkit.getOnlinePlayers().forEach(this::load);
  }

  @Override
  public void stop() {}

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    this.load(event.getPlayer());
  }

  private void load(Player player) {
    this.profiles.load(
        player.getUniqueId(),
        player.getName(),
        player.getDisplayName()
      )
      .thenAccept(profile ->
        this.plugin.getServer()
          .getScheduler()
          .runTask((Plugin) this.plugin, () -> {
            if (player.isOnline()) {
              this.plugin.getServer()
                .getPluginManager()
                .callEvent(
                  (Event) new PlayerLocaleUpdateEvent(
                    player,
                    profile.language(),
                    PlayerLocaleUpdateEvent.Cause.PROFILE_LOADED
                  )
                );
            }
          })
      )
      .exceptionally(error -> {
        this.plugin.getLogger().warning(
          "Cannot load JSON profile for " +
          player.getName() +
          ": " +
          error.getMessage()
        );
        return null;
      });
  }
}
