package dev.bisz.combat.commands;

import dev.bisz.combat.PVPStatusChangeEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class PVPPassiveListener implements Listener {

  private static final long PVP_DURATION_MILLIS = 15_000L;

  private final Set<Player> disabled = new HashSet<>();
  private final Map<UUID, Long> pvpExpiryTimes = new HashMap<>();
  private final BukkitTask countdownTask;

  public PVPPassiveListener(JavaPlugin plugin) {
    this.countdownTask = plugin
      .getServer()
      .getScheduler()
      .runTaskTimer(plugin, this::updatePvpStatuses, 20L, 20L);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void protectPassivePlayers(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player)) {
      return;
    }

    Player victim = (Player) event.getEntity();
    Player attacker = PlayerDamageSourceResolver.resolve(event.getDamager());
    if (
      attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())
    ) {
      return;
    }

    if (!hasPVPEnabled(attacker) || !hasPVPEnabled(victim)) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerPvpDamage(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player)) {
      return;
    }

    Player victim = (Player) event.getEntity();
    Player attacker = PlayerDamageSourceResolver.resolve(event.getDamager());
    if (
      attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())
    ) {
      return;
    }

    if (hasPVPEnabled(attacker) && hasPVPEnabled(victim)) {
      startPvpStatus(attacker);
      startPvpStatus(victim);
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    pvpExpiryTimes.remove(event.getPlayer().getUniqueId());
    disabled.remove(event.getPlayer());
  }

  public void disablePVP(Player player) {
    disabled.add(player);
    fireCurrentStatus(player);
  }

  public void enablePVP(Player player) {
    disabled.remove(player);
    fireCurrentStatus(player);
  }

  public boolean hasPVPEnabled(Player player) {
    return !disabled.contains(player);
  }

  public boolean isInPvpStatus(Player player) {
    Long expiryTime = pvpExpiryTimes.get(player.getUniqueId());
    return expiryTime != null && expiryTime > System.currentTimeMillis();
  }

  public void dispose() {
    countdownTask.cancel();
    pvpExpiryTimes.clear();
    disabled.clear();
  }

  private void startPvpStatus(Player player) {
    pvpExpiryTimes.put(
      player.getUniqueId(),
      System.currentTimeMillis() + PVP_DURATION_MILLIS
    );
    fireStatusChange(player, true, 15);
  }

  private void fireCurrentStatus(Player player) {
    Long expiryTime = pvpExpiryTimes.get(player.getUniqueId());
    long remainingMillis = expiryTime == null
      ? 0L
      : expiryTime - System.currentTimeMillis();
    if (remainingMillis <= 0L) {
      fireStatusChange(player, false, 0);
      return;
    }
    fireStatusChange(player, true, (int) Math.ceil(remainingMillis / 1_000.0D));
  }

  private void updatePvpStatuses() {
    long now = System.currentTimeMillis();
    for (
      java.util.Iterator<Map.Entry<UUID, Long>> iterator = pvpExpiryTimes
        .entrySet()
        .iterator();
      iterator.hasNext();

    ) {
      Map.Entry<UUID, Long> entry = iterator.next();
      Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
      if (player == null || !player.isOnline()) {
        iterator.remove();
        continue;
      }

      long remainingMillis = entry.getValue() - now;
      if (remainingMillis <= 0L) {
        iterator.remove();
        fireStatusChange(player, false, 0);
        continue;
      }

      int remainingSeconds = (int) Math.ceil(remainingMillis / 1_000.0D);
      fireStatusChange(player, true, remainingSeconds);
    }
  }

  private void fireStatusChange(
    Player player,
    boolean inPvp,
    int remainingSeconds
  ) {
    player
      .getServer()
      .getPluginManager()
      .callEvent(
        new PVPStatusChangeEvent(
          player,
          inPvp,
          hasPVPEnabled(player),
          remainingSeconds
        )
      );
  }
}
