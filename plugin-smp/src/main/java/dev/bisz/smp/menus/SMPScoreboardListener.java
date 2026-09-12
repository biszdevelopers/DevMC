package dev.bisz.smp.menus;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.chat.ChatUtils;
import dev.bisz.combat.*;
import dev.bisz.currency.PurseUpdateEvent;
import dev.bisz.players.locales.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class SMPScoreboardListener implements Listener {

  private final JavaPlugin plugin;
  private final CombatService combat;
  private final Map<UUID, SMPScoreboard> boards = new HashMap<>();
  private final BukkitTask reminders;

  public SMPScoreboardListener(JavaPlugin plugin) {
    this.plugin = plugin;
    combat = plugin.getServer().getServicesManager().load(CombatService.class);
    reminders = plugin
      .getServer()
      .getScheduler()
      .runTaskTimer(plugin, this::remind, 6000, 6000);
  }

  public void show(Player p) {
    dispose(p);
    SMPScoreboard board = new SMPScoreboard(plugin, p);
    if (combat != null) {
      board.updatePvpStatus(combat.isInPvp(p), combat.hasPvpEnabled(p), 0);
      board.ghost(combat.isGhost(p));
    }
    boards.put(p.getUniqueId(), board);
  }

  @EventHandler
  public void join(PlayerJoinEvent e) {
    show(e.getPlayer());
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    dispose(e.getPlayer());
  }

  @EventHandler
  public void locale(PlayerLocaleUpdateEvent e) {
    if (e.player().isOnline()) show(e.player());
  }

  @EventHandler
  public void purse(PurseUpdateEvent e) {
    SMPScoreboard b = boards.get(e.getPlayer().getUniqueId());
    if (b != null) b.updatePurse(e.getFinalAmount());
  }

  @EventHandler
  public void pvp(PVPStatusChangeEvent e) {
    SMPScoreboard b = boards.get(e.getPlayer().getUniqueId());
    if (b != null) b.updatePvpStatus(
      e.isInPvp(),
      e.isPvpEnabled(),
      e.getRemainingSeconds()
    );
  }

  @EventHandler
  public void ghost(GhostStateChangeEvent e) {
    SMPScoreboard b = boards.get(e.player().getUniqueId());
    if (b != null) b.ghost(e.ghost());
  }

  private void remind() {
    var p = BundlerPlugin.instance()
      .stashService()
      .find(NamespacedKey.fromString("combat:death_recovery"));
    if (p.isEmpty()) return;
    for (Player player : plugin.getServer().getOnlinePlayers())
      if (!p.get().isEmpty(player.getUniqueId())) {
        TextComponent t = new TextComponent(
          Locale.get(player, "combat.stash.reminder")
        );
        ChatUtils.attachCommand(
          t,
          "deathstash",
          Locale.get(player, "combat.stash.hover")
        );
        player.spigot().sendMessage(t);
      }
  }

  public void disposeAll() {
    for (SMPScoreboard b : new ArrayList<>(boards.values())) b.dispose();
    boards.clear();
    reminders.cancel();
  }

  private void dispose(Player p) {
    SMPScoreboard b = boards.remove(p.getUniqueId());
    if (b != null) b.dispose();
  }
}
