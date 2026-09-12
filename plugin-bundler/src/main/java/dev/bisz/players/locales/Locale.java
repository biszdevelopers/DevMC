package dev.bisz.players.locales;

import dev.bisz.bundler.BundlerPlugin;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;

/** Legacy-style static access to Bundler's player translations. */
public final class Locale {

  public enum Language {
    EN_US,
    ZH_CN,
    ZH_CN_MOJANG,
    ZH_TW,
    JA_JP,
    KO_KR,
    ES_ES,
  }

  private Locale() {}

  public static String get(Player player, String key, Object... arguments) {
    return service().translate(service().audience(player), key, arguments);
  }

  public static String get(Language language, String key, Object... arguments) {
    return service().translate(languageKey(language), key, arguments);
  }

  public static String get(String language, String key, Object... arguments) {
    return service().translate(language, key, arguments);
  }

  public static String getMojang(
    Player player,
    String key,
    Object... arguments
  ) {
    return service().minecraft(player, key, arguments);
  }

  public static String getMojang(
    Language language,
    String key,
    Object... arguments
  ) {
    return service().minecraft(languageKey(language), key, arguments);
  }

  public static String getMojang(
    String language,
    String key,
    Object... arguments
  ) {
    return service().minecraft(language, key, arguments);
  }

  public static Language getLanguage(Player player) {
    return Language.valueOf(
      service().audience(player).language().toUpperCase(java.util.Locale.ROOT)
    );
  }

  public static String language(Player player) {
    return service().audience(player).language();
  }

  public static CompletionStage<Void> changeLanguage(
    Player player,
    Language language
  ) {
    return service().changeLanguage(player, languageKey(language));
  }

  public static List<String> availableLanguages() {
    return service().availableLanguages();
  }

  public static void reload() {
    service().reload();
  }

  private static LocaleService service() {
    return BundlerPlugin.instance().localeService();
  }

  private static String languageKey(Language language) {
    return language
      .name()
      .toLowerCase(java.util.Locale.ROOT)
      .replace("_mojang", "");
  }
}
