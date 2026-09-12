/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Event
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package dev.bisz.players.locales.internal;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.players.ProfileService;
import dev.bisz.players.locales.LocaleService;
import dev.bisz.players.locales.LocalizedAudience;
import dev.bisz.players.locales.PlayerLocaleUpdateEvent;
import dev.bisz.storage.JsonDatabase;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class DefaultLocaleService implements LocaleService {

  private static final List<String> LANGUAGES = List.of(
    "zh_cn",
    "zh_tw",
    "en_us",
    "ja_jp",
    "ko_kr",
    "es_es"
  );
  private static final List<String> DEFAULT_FILES = List.of(
    "zh_cn.json",
    "zh_tw.json",
    "en_us.json",
    "ja_jp.json",
    "ko_kr.json",
    "es_es.json",
    "zh_cn_mojang.json",
    "zh_tw_mojang.json",
    "en_us_mojang.json",
    "ja_jp_mojang.json",
    "ko_kr_mojang.json",
    "es_es_mojang.json"
  );
  private final ProfileService profiles;
  private final JsonDatabase database;
  private final JavaPlugin plugin;
  private volatile Map<String, Map<String, String>> locales = Map.of();

  public DefaultLocaleService(
    ProfileService profiles,
    JsonDatabase database,
    JavaPlugin plugin
  ) {
    this.profiles = profiles;
    this.database = database;
    this.plugin = plugin;
    this.installDefaults(plugin);
    this.reload();
  }

  @Override
  public LocalizedAudience audience(Player player) {
    String language = this.profiles.cached(player)
      .map(profile -> profile.language())
      .orElse("zh_cn");
    return new LocalizedAudience(
      player.getUniqueId(),
      DefaultLocaleService.normalize(language)
    );
  }

  @Override
  public String translate(
    LocalizedAudience audience,
    String key,
    Object... arguments
  ) {
    return this.translate(audience.language(), key, arguments);
  }

  @Override
  public String translate(String language, String key, Object... arguments) {
    return this.lookup(DefaultLocaleService.normalize(language), key).formatted(
      arguments
    );
  }

  @Override
  public String minecraft(Player player, String key, Object... arguments) {
    return this.minecraft(this.audience(player).language(), key, arguments);
  }

  @Override
  public String minecraft(String language, String key, Object... arguments) {
    String selected = DefaultLocaleService.normalize(language) + "_mojang";
    String value = this.lookup(selected, key);
    if (value.equals(key)) {
      value = this.lookup("en_us_mojang", key);
    }
    return value.formatted(arguments);
  }

  @Override
  public CompletionStage<Void> changeLanguage(Player player, String language) {
    String normalized = DefaultLocaleService.normalize(language);
    if (!LANGUAGES.contains(normalized)) {
      throw new IllegalArgumentException("Unsupported language: " + language);
    }
    return this.profiles.updateLanguage(
        player.getUniqueId(),
        normalized
      ).thenAccept(profile ->
        this.plugin.getServer()
          .getScheduler()
          .runTask((Plugin) this.plugin, () -> {
            if (player.isOnline()) {
              this.plugin.getServer()
                .getPluginManager()
                .callEvent(
                  (Event) new PlayerLocaleUpdateEvent(
                    player,
                    profile.language(),
                    PlayerLocaleUpdateEvent.Cause.LANGUAGE_CHANGED
                  )
                );
            }
          })
      );
  }

  @Override
  public List<String> availableLanguages() {
    return LANGUAGES;
  }

  @Override
  public int translationCount(String language) {
    return this.locales.getOrDefault(
      DefaultLocaleService.normalize(language),
      Map.of()
    ).size();
  }

  @Override
  public double translationCoverage(String language) {
    int largest = LANGUAGES.stream()
      .mapToInt(this::translationCount)
      .max()
      .orElse(0);
    return largest == 0
      ? 100.0
      : (translationCount(language) * 100.0) / largest;
  }

  @Override
  public void reload() {
    LinkedHashMap<String, Map<String, String>> replacement = new LinkedHashMap<
      String,
      Map<String, String>
    >();
    for (String language : LANGUAGES) {
      replacement.put(language, this.load("lang/" + language + ".json"));
      replacement.put(
        language + "_mojang",
        this.load("lang/" + language + "_mojang.json")
      );
    }
    this.locales = Map.copyOf(replacement);
  }

  private Map<String, String> load(String name) {
    LinkedHashMap<String, String> values = new LinkedHashMap<String, String>();
    for (Map.Entry<String, Object> entry : this.database.loadDataFromDataBase(
      name
    ).entrySet()) {
      Object object = entry.getValue();
      if (!(object instanceof String)) continue;
      String value = (String) object;
      values.put(entry.getKey().toLowerCase(Locale.ROOT), value);
    }
    return Map.copyOf(values);
  }

  private String lookup(String language, String key) {
    String normalizedKey = key.toLowerCase(Locale.ROOT);
    Map selected = this.locales.getOrDefault(language, Map.of());
    String value = (String) selected.get(normalizedKey);
    if (value != null) {
      return value;
    }
    return this.locales.getOrDefault("zh_cn", Map.of()).getOrDefault(
      normalizedKey,
      key
    );
  }

  private void installDefaults(JavaPlugin plugin) {
    for (String name : DEFAULT_FILES) {
      try (InputStream input = bundledResource(plugin, "lang/" + name)) {
        if (input == null) {
          throw new IllegalStateException(
            "Missing bundled locale file " + name
          );
        }
        if (this.database.installDefault("lang/" + name, input)) {
          continue;
        }
        try (InputStream defaults = bundledResource(plugin, "lang/" + name)) {
          if (defaults == null) {
            throw new IllegalStateException(
              "Missing bundled locale file " + name
            );
          }
          this.database.mergeMissingDefaults("lang/" + name, defaults);
        }
      } catch (IOException exception) {
        throw new IllegalStateException(
          "Cannot close bundled locale file " + name,
          exception
        );
      }
    }
  }

  private static InputStream bundledResource(JavaPlugin plugin, String name) {
    if (plugin instanceof BundlerPlugin bundler) return bundler.bundledResource(
      name
    );
    InputStream input = plugin.getResource(name);
    if (input == null) input = plugin
      .getClass()
      .getClassLoader()
      .getResourceAsStream(name);
    if (input == null) input = DefaultLocaleService.class.getResourceAsStream(
      "/" + name
    );
    return input;
  }

  private static String normalize(String language) {
    return language.toLowerCase(Locale.ROOT).replace('-', '_');
  }
}
