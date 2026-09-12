package dev.bisz.combat;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.bundler.JSON;
import dev.bisz.combat.commands.PVPPassiveCommand;
import dev.bisz.combat.commands.PVPPassiveListener;
import dev.bisz.combat.items.CorpseHeadItem;
import dev.bisz.combat.items.ExperienceBottleItem;
import dev.bisz.commands.CommandRegistery;
import dev.bisz.items.ItemsPlugin;
import dev.bisz.players.locales.Locale;
import java.io.InputStream;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Lifecycle entry point for the combat plugin. */
public final class CombatPlugin extends JavaPlugin {

  private PVPPassiveListener pvpListener;
  private DeathManager deaths;

  @Override
  public void onEnable() {
    this.pvpListener = new PVPPassiveListener(this);
    this.getServer()
      .getPluginManager()
      .registerEvents((Listener) this.pvpListener, (Plugin) this);
    CommandRegistery.register(this, new PVPPassiveCommand(this.pvpListener));
    ItemsPlugin.instance()
      .registry()
      .registerAll(
        this,
        java.util.List.of(new CorpseHeadItem(), new ExperienceBottleItem())
      );
    installLocales();
    this.deaths = new DeathManager(
      this,
      new DeathManager.PVPPassiveFacade() {
        public boolean enabled(org.bukkit.entity.Player p) {
          return pvpListener.hasPVPEnabled(p);
        }

        public boolean active(org.bukkit.entity.Player p) {
          return pvpListener.isInPvpStatus(p);
        }
      }
    );
    getServer()
      .getServicesManager()
      .register(
        CombatService.class,
        this.deaths,
        this,
        org.bukkit.plugin.ServicePriority.Normal
      );
    CommandRegistery.register(this, new ReviveCommand(this.deaths));
    CommandRegistery.register(this, new DeathStashCommand(this.deaths));
  }

  @Override
  public void onDisable() {
    if (this.pvpListener != null) {
      this.pvpListener.dispose();
    }
    if (this.deaths != null) this.deaths.dispose();
    getServer().getServicesManager().unregisterAll(this);
    if (ItemsPlugin.instance() != null) ItemsPlugin.instance()
      .registry()
      .unregisterAll(this);
    CommandRegistery.unregisterAll(this);
  }

  public CombatService combatService() {
    return deaths;
  }

  private void installLocales() {
    for (String language : Locale.availableLanguages()) {
      String resource = "lang/" + language + ".json";
      try (InputStream input = getResource(resource)) {
        if (input != null) JSON.mergeMissingDefaults(resource, input);
      } catch (java.io.IOException e) {
        throw new IllegalStateException("Cannot install " + resource, e);
      }
    }
    Locale.reload();
  }
}
