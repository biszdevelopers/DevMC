package dev.bisz.enchants;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

/** Replaces the vanilla XP curve while leaving every XP source's point yield intact. */
final class LinearExperienceListener implements Listener {
  private final EnchantsPlugin plugin;
  private final NamespacedKey migrated;

  LinearExperienceListener(EnchantsPlugin plugin) {
    this.plugin = plugin;
    migrated = new NamespacedKey(plugin, "linear_experience");
    plugin.getServer().getScheduler().runTask(plugin,
      () -> plugin.getServer().getOnlinePlayers().forEach(this::normalize));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  void gainExperience(PlayerExpChangeEvent event) {
    normalize(event.getPlayer());
    int points = event.getAmount();
    event.setAmount(0);
    LinearExperience.addPoints(event.getPlayer(), points);
    flashActionBar(event.getPlayer());
  }

  @EventHandler
  void join(PlayerJoinEvent event) {
    normalize(event.getPlayer());
  }

  private void flashActionBar(Player player) {
    if (!plugin.config().config().getBoolean("display.xp-action-bar", true)) return;
    int level = player.getLevel();
    int current = LinearExperience.totalPoints(player) % LinearExperience.pointsPerLevel();
    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(
      "§aLevel " + level + " §7— §e" + current + "/" + LinearExperience.pointsPerLevel() + " XP"));
  }

  private void normalize(Player player) {
    if (player.getPersistentDataContainer().has(migrated, PersistentDataType.BYTE)) return;
    int existingPoints = LinearExperience.vanillaTotalPoints(player.getLevel(), player.getExp());
    player.getPersistentDataContainer().set(migrated, PersistentDataType.BYTE, (byte) 1);
    LinearExperience.setTotalPoints(player, existingPoints);
  }
}
