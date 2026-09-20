package dev.bisz.worldgen.listener;

import dev.bisz.worldgen.WorldGenPlugin;
import dev.bisz.worldgen.model.ChunkKey;
import dev.bisz.worldgen.wilderness.ChunkState;
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
 * Optional action-bar overlay showing the current chunk index and the chunk's
 * resource and regeneration indicators.
 */
public final class IndicatorHud implements Listener {

  private final WorldGenPlugin plugin;
  private final Set<UUID> enabled = new HashSet<>();
  private BukkitTask task;

  public IndicatorHud(WorldGenPlugin plugin) {
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
    ChunkState state = plugin.indicators().get(chunk);

    String resource = state == null
      ? "--"
      : String.format(java.util.Locale.ROOT, "%.2f", state.resource());
    String regen;
    if (state == null || !state.isScheduled()) {
      regen = "--";
    } else {
      regen = formatDuration(
        Math.max(0L, state.dueAt() - System.currentTimeMillis())
      );
    }

    String text =
      "§7chunk §f" +
      chunk.x() +
      "," +
      chunk.z() +
      " §8| §ares " +
      resource +
      " §8| §eregen " +
      regen;

    player
      .spigot()
      .sendMessage(
        ChatMessageType.ACTION_BAR,
        TextComponent.fromLegacyText(text)
      );
  }

  private static String formatDuration(long millis) {
    long minutes = millis / 60_000L;
    if (minutes <= 0L) return (millis / 1000L) + "s";
    if (minutes < 60L) return minutes + "m";
    return (minutes / 60L) + "h" + (minutes % 60L) + "m";
  }
}
