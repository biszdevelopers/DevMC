/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.Sound
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package dev.bisz.players.locales;

import dev.bisz.commands.DevCommand;
import dev.bisz.menus.MenuManager;
import dev.bisz.players.locales.LocaleService;
import dev.bisz.players.locales.LocalizedAudience;
import java.util.List;
import java.util.Locale;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class LocaleCommand extends DevCommand {

  private final JavaPlugin plugin;
  private final LocaleService locales;
  private final LanguageSelectionMenu languageMenu;

  public LocaleCommand(
    JavaPlugin plugin,
    LocaleService locales,
    MenuManager menus
  ) {
    super("locale", "");
    this.plugin = plugin;
    this.locales = locales;
    this.languageMenu = new LanguageSelectionMenu(plugin, locales, menus);
    this.setAliases(List.of("lang"));
    this.setDescription("Shows or changes your Bundler language.");
    this.setUsage("/locale [language|reload]");
  }

  @Override
  protected boolean executeCommand(
    CommandSender sender,
    String label,
    String[] arguments
  ) {
    if (arguments.length == 1 && arguments[0].equalsIgnoreCase("reload")) {
      if (!sender.hasPermission("bundler.admin")) {
        sender.sendMessage("You do not have permission to reload locales.");
        return true;
      }
      this.locales.reload();
      sender.sendMessage("Locale files reloaded.");
      return true;
    }
    if (!(sender instanceof Player)) {
      sender.sendMessage(
        "Players use /locale <language>; console may use /locale reload."
      );
      return true;
    }
    Player player = (Player) sender;
    LocalizedAudience audience = this.locales.audience(player);
    String available = String.join(
      (CharSequence) ", ",
      this.locales.availableLanguages()
    );
    if (arguments.length == 0) {
      this.languageMenu.open(player);
      return true;
    }
    String language = LocaleCommand.normalize(arguments[0]);
    if (!this.locales.availableLanguages().contains(language)) {
      player.sendMessage(
        this.locales.translate(
          audience,
          "locale.invalid",
          arguments[0],
          available
        )
      );
      return true;
    }
    this.locales.changeLanguage(player, language).whenComplete(
        (ignored, error) ->
          this.plugin.getServer()
            .getScheduler()
            .runTask((Plugin) this.plugin, () -> {
              if (error != null) {
                player.sendMessage(
                  "Could not save your language. Check the server log."
                );
                this.plugin.getLogger().warning(
                  "Cannot save locale for " +
                  player.getName() +
                  ": " +
                  error.getMessage()
                );
                return;
              }
              LocalizedAudience updated = this.locales.audience(player);
              player.sendMessage(
                this.locales.translate(updated, "locale.changed", new Object[0])
              );
              player.playSound(
                player.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_PLING,
                1.0f,
                2.0f
              );
            })
      );
    return true;
  }

  @Override
  protected List<String> complete(
    CommandSender sender,
    String alias,
    String[] arguments
  ) {
    if (arguments.length != 1) {
      return List.of();
    }
    String prefix = LocaleCommand.normalize(arguments[0]);
    return this.locales.availableLanguages()
      .stream()
      .filter(language -> language.startsWith(prefix))
      .toList();
  }

  private static String normalize(String language) {
    return language.toLowerCase(Locale.ROOT).replace('-', '_');
  }
}
