package dev.bisz.world.listener;

import dev.bisz.world.WorldPlugin;
import dev.bisz.world.model.ChunkKey;
import dev.bisz.world.model.ZoneType;
import dev.bisz.world.wilderness.ChunkState;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Optional action-bar overlay showing the current zone, region group, chunk
 * index, and the region's resource and visibility indicators.
 */
public final class IndicatorHud implements Listener {

  private final WorldPlugin plugin;
  private final Set<UUID> enabled = new HashSet<>();
  private BukkitTask task;

  public IndicatorHud(WorldPlugin plugin) {
    this.plugin = plugin;
  }

  /** Starts the refresh loop. */
  public void start() {
    if (task != null) return;
    task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
  }

  /** Stops the refresh loop. */
  public void stop() {
    if (task != null) {
      task.cancel();
      task = null;
    }
    enabled.clear();
  }

  /** Toggles the overlay for a player. */
  public boolean toggle(Player player) {
    UUID id = player.getUniqueId();
    if (enabled.remove(id)) return false;
    enabled.add(id);
    render(player);
    return true;
  }

  public boolean isEnabled(Player player) {
    return enabled.contains(player.getUniqueId());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    enabled.remove(event.getPlayer().getUniqueId());
  }

  private void tick() {
    if (enabled.isEmpty()) return;
    for (UUID id : Set.copyOf(enabled)) {
      Player player = Bukkit.getPlayer(id);
      if (player == null || !player.isOnline()) {
        enabled.remove(id);
        continue;
      }
      render(player);
    }
  }

  private void render(Player player) {
    ChunkKey chunk = ChunkKey.of(player.getLocation());
    ZoneType zone = plugin.settlements().zoneAt(player.getLocation());
    ChunkState state = plugin.indicators().get(chunk);

    String resource = state == null
      ? "--"
      : String.format(java.util.Locale.ROOT, "%.2f", state.resource());
    String visibility = state == null
      ? "--"
      : String.format(java.util.Locale.ROOT, "%.2f", state.visibility());

    String text =
      "§7" +
      (zone == null ? "unmanaged" : zone.name()) +
      " §8| §7chunk §f" +
      chunk.x() +
      "," +
      chunk.z() +
      " §8| §ares " +
      resource +
      " §8| §evis " +
      visibility;

    player
      .spigot()
      .sendMessage(
        ChatMessageType.ACTION_BAR,
        TextComponent.fromLegacyText(text)
      );
  }
}
