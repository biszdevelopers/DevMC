/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 */
package dev.bisz.npc.internal;

import dev.bisz.bundler.internal.BundlerModule;
import dev.bisz.bundler.internal.ModuleContext;
import dev.bisz.npc.NpcInteractionEvent;
import dev.bisz.npc.NpcService;
import dev.bisz.npc.internal.CitizensNpcService;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public final class NpcModule implements BundlerModule, Listener {

  private CitizensNpcService service;

  @Override
  public String id() {
    return "npc";
  }

  @Override
  public Set<String> dependencies() {
    return Set.of();
  }

  @Override
  public void start(ModuleContext context) {
    if (Bukkit.getPluginManager().getPlugin("Citizens") == null) {
      throw new IllegalStateException("Citizens is not installed");
    }
    this.service = new CitizensNpcService();
    context.services().register(NpcService.class, this.service);
    Bukkit.getPluginManager().registerEvents(this, context.plugin());
  }

  @Override
  public void stop() {
    if (service != null) service.dispose();
  }

  @EventHandler
  public void onInteract(PlayerInteractEntityEvent event) {
    if (service == null) return;
    service
      .all()
      .stream()
      .filter(
        h ->
          h.entity() != null &&
          h.entity().getUniqueId().equals(event.getRightClicked().getUniqueId())
      )
      .findFirst()
      .ifPresent(h -> {
        event.setCancelled(true);
        Bukkit.getPluginManager().callEvent(
          new NpcInteractionEvent(event.getPlayer(), h)
        );
      });
  }
}
