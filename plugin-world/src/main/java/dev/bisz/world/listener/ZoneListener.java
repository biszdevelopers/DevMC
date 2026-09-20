package dev.bisz.world.listener;

import dev.bisz.world.WorldPlugin;
import dev.bisz.world.model.ZoneType;
import dev.bisz.world.police.SearchService;
import dev.bisz.players.locales.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Reports the current zone and searches players entering policed space. */
public final class ZoneListener implements Listener {

  private final WorldPlugin plugin;
  private final Map<UUID, ZoneType> lastZone = new HashMap<>();

  public ZoneListener(WorldPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    showZone(event.getPlayer());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    lastZone.remove(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onMove(PlayerMoveEvent event) {
    if (
      event.getFrom().getBlockX() == event.getTo().getBlockX() &&
      event.getFrom().getBlockZ() == event.getTo().getBlockZ() &&
      event.getFrom().getWorld() == event.getTo().getWorld()
    ) return;
    Player player = event.getPlayer();
    ZoneType zone = plugin.settlements().zoneAt(event.getTo());
    ZoneType previous = lastZone.put(player.getUniqueId(), zone);
    if (zone == previous) return;
    showZone(player, zone);
    if (zone == ZoneType.POLICED) {
      SearchService.Result result = plugin.search().confiscate(player);
      if (result.confiscated() > 0) {
        player.sendMessage(
          Locale.get(
            player,
            "settlement.police.confiscated",
            result.confiscated()
          )
        );
      }
    }
  }

  private void showZone(Player player) {
    ZoneType zone = plugin.settlements().zoneAt(player.getLocation());
    lastZone.put(player.getUniqueId(), zone);
    showZone(player, zone);
  }

  private void showZone(Player player, ZoneType zone) {
    if (zone == null) return;
    String label = Locale.get(
      player,
      "settlement.zone." + zone.name().toLowerCase(java.util.Locale.ROOT)
    );
    player
      .spigot()
      .sendMessage(
        ChatMessageType.ACTION_BAR,
        TextComponent.fromLegacyText(
          Locale.get(player, "settlement.zone.hud", label)
        )
      );
  }
}
