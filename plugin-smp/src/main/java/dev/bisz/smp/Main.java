/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  dev.bisz.bundler.BundlerPlugin
 *  dev.bisz.commands.DevCommand
 *  org.bukkit.Bukkit
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package dev.bisz.smp;

import dev.bisz.bundler.JSON;
import dev.bisz.commands.CommandRegistery;
import dev.bisz.commands.DevCommand;
import dev.bisz.players.locales.Locale;
import dev.bisz.smp.commands.ForceDailyNitsCommand;
import dev.bisz.smp.commands.SitCommand;
import dev.bisz.smp.menus.SMPScoreboard;
import dev.bisz.smp.menus.SMPScoreboardListener;
import java.io.IOException;
import java.io.InputStream;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

  private SMPScoreboardListener scoreboardListener;

  public void onEnable() {
    installLocaleDefaults();
    this.scoreboardListener = new SMPScoreboardListener(this);
    this.getServer()
      .getPluginManager()
      .registerEvents((Listener) new SitCommand(), (Plugin) this);
    this.getServer()
      .getPluginManager()
      .registerEvents((Listener) this.scoreboardListener, (Plugin) this);
    CommandRegistery.register((Plugin) this, (DevCommand) new SitCommand());
    CommandRegistery.register(
      (Plugin) this,
      (DevCommand) new ForceDailyNitsCommand()
    );
    Bukkit.getOnlinePlayers().forEach(this.scoreboardListener::show);
  }

  public void onDisable() {
    if (this.scoreboardListener != null) {
      this.scoreboardListener.disposeAll();
    }
  }

  private void installLocaleDefaults() {
    for (String language : Locale.availableLanguages()) {
      String resource = "lang/" + language + ".json";
      try (InputStream input = getResource(resource)) {
        if (input == null) throw new IllegalStateException(
          "Missing bundled SMP locale file " + resource
        );
        JSON.mergeMissingDefaults(resource, input);
      } catch (IOException exception) {
        throw new IllegalStateException(
          "Cannot install bundled SMP locale file " + resource,
          exception
        );
      }
    }
    Locale.reload();
  }
}
