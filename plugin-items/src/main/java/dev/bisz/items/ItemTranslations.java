/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  dev.bisz.bundler.BundlerPlugin
 *  dev.bisz.players.locales.LocaleService
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package dev.bisz.items;

import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ItemTranslations {

  static final String CONSOLE_LANGUAGE = "en_us";

  private ItemTranslations() {}

  static String language(Player viewer) {
    if (viewer == null) {
      return null;
    }
    return ItemTranslations.normalize(
      dev.bisz.players.locales.Locale.language(viewer)
    );
  }

  static String language(CommandSender sender) {
    String string;
    if (sender instanceof Player) {
      Player player = (Player) sender;
      string = ItemTranslations.language(player);
    } else {
      string = CONSOLE_LANGUAGE;
    }
    return string;
  }

  static String translate(
    String language,
    String key,
    String fallback,
    Object... arguments
  ) {
    try {
      String translated = dev.bisz.players.locales.Locale.get(
        ItemTranslations.normalize(language),
        key,
        arguments
      );
      return translated.equals(key)
        ? fallback.formatted(arguments)
        : translated;
    } catch (RuntimeException ignored) {
      return fallback.formatted(arguments);
    }
  }

  static String minecraft(
    String language,
    String key,
    String fallback,
    Object... arguments
  ) {
    try {
      String translated = dev.bisz.players.locales.Locale.getMojang(
        ItemTranslations.normalize(language),
        key,
        arguments
      );
      return translated.equals(key)
        ? fallback.formatted(arguments)
        : translated;
    } catch (RuntimeException ignored) {
      return fallback.formatted(arguments);
    }
  }

  public static String forSender(
    CommandSender sender,
    String key,
    String fallback,
    Object... arguments
  ) {
    return ItemTranslations.translate(
      ItemTranslations.language(sender),
      key,
      fallback,
      arguments
    );
  }

  static String humanize(String value) {
    String spaced = value.replace('_', ' ').replace('-', ' ');
    if (spaced.isEmpty()) {
      return value;
    }
    return (
      spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1)
    );
  }

  static String normalize(String language) {
    return language == null
      ? null
      : language.toLowerCase(Locale.ROOT).replace('-', '_');
  }
}
