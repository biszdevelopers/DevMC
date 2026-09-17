package dev.bisz.city.listener;

import dev.bisz.city.CityPlugin;
import dev.bisz.city.model.CrimeType;
import dev.bisz.city.model.ZoneType;
import dev.bisz.players.locales.Locale;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Disables PvP in policed zones and reports the offender. */
public final class PvpListener implements Listener {

  private final CityPlugin plugin;

  public PvpListener(CityPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onDamage(EntityDamageByEntityEvent event) {
    if (
      !(event.getDamager() instanceof Player attacker) ||
      !(event.getEntity() instanceof Player victim)
    ) return;
    if (plugin.cities().zoneAt(victim.getLocation()) != ZoneType.POLICED) {
      return;
    }
    event.setCancelled(true);
    plugin.police().report(attacker, CrimeType.ASSAULT, victim.getLocation());
    attacker.sendMessage(Locale.get(attacker, "city.police.assault"));
  }
}
