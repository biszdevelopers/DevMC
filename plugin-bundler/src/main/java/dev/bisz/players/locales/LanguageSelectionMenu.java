package dev.bisz.players.locales;

import dev.bisz.menus.MenuItem;
import dev.bisz.menus.MenuManager;
import dev.bisz.menus.SinglePageMenuTemplate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Bundler's flag-head language selection menu. */
public final class LanguageSelectionMenu {

  private static final Map<String, LanguageOption> OPTIONS = options();

  private final JavaPlugin plugin;
  private final LocaleService locales;
  private final MenuManager menus;

  /** Creates the reusable language menu implementation. */
  public LanguageSelectionMenu(
    JavaPlugin plugin,
    LocaleService locales,
    MenuManager menus
  ) {
    this.plugin = plugin;
    this.locales = locales;
    this.menus = menus;
  }

  /** Opens the selector for a player. */
  public void open(Player player) {
    SinglePageMenuTemplate.Builder builder = SinglePageMenuTemplate.builder(
      viewer ->
        locales.translate(locales.audience(viewer), "locale.menu.title"),
      3
    );
    MenuItem background = MenuItem.builder(Material.BLACK_STAINED_GLASS_PANE)
      .name(" ")
      .build();
    for (int slot = 0; slot < 27; slot++) builder.item(slot, background);
    int slot = 10;
    for (Map.Entry<String, LanguageOption> entry : OPTIONS.entrySet()) {
      String code = entry.getKey();
      LanguageOption option = entry.getValue();
      MenuItem item = MenuItem.builder(Material.PLAYER_HEAD)
        .skullTexture(option.texture())
        .name("§a" + option.nativeName())
        .lore(viewer -> languageLore(viewer, code))
        .onClick(context -> select(context.player(), context.session(), code))
        .build();
      builder.item(slot++, item);
    }
    builder.item(
      22,
      MenuItem.builder(Material.BARRIER)
        .localizedName("locale.menu.close")
        .onClick(context -> context.session().close())
        .build()
    );
    builder.onOpen(context ->
      context
        .player()
        .playSound(
          context.player().getLocation(),
          Sound.ENTITY_VILLAGER_TRADE,
          1.0f,
          1.0f
        )
    );
    menus.open(player, builder.build());
  }

  private List<String> languageLore(Player player, String code) {
    LocalizedAudience audience = locales.audience(player);
    String coverage = String.format(
      java.util.Locale.ROOT,
      "%.0f%%",
      locales.translationCoverage(code)
    );
    String stateKey = audience.language().equals(code)
      ? "locale.menu.current"
      : "locale.menu.select";
    return List.of(
      locales.translate(audience, "locale.menu.coverage", coverage),
      " ",
      locales.translate(audience, stateKey)
    );
  }

  private void select(
    Player player,
    dev.bisz.menus.MenuSession session,
    String code
  ) {
    locales
      .changeLanguage(player, code)
      .whenComplete((ignored, error) ->
        plugin
          .getServer()
          .getScheduler()
          .runTask((Plugin) plugin, () -> {
            if (!player.isOnline()) return;
            if (error != null) {
              player.sendMessage(
                locales.translate(
                  locales.audience(player),
                  "locale.menu.save_failed"
                )
              );
              plugin
                .getLogger()
                .warning(
                  "Cannot save locale for " +
                  player.getName() +
                  ": " +
                  error.getMessage()
                );
              return;
            }
            player.sendMessage(
              locales.translate(locales.audience(player), "locale.changed")
            );
            player.playSound(
              player.getLocation(),
              Sound.BLOCK_NOTE_BLOCK_PLING,
              1.0f,
              2.0f
            );
            try {
              session.close();
            } catch (IllegalStateException alreadyClosed) {
              // The player may have closed the menu while the profile write was pending.
            }
          })
      );
  }

  private static Map<String, LanguageOption> options() {
    LinkedHashMap<String, LanguageOption> values = new LinkedHashMap<>();
    values.put(
      "zh_cn",
      new LanguageOption(
        "中文（简体）",
        "https://textures.minecraft.net/texture/7f9bc035cdc80f1ab5e1198f29f3ad3fdd2b42d9a69aeb64de990681800b98dc"
      )
    );
    values.put(
      "zh_tw",
      new LanguageOption(
        "中文（繁體）",
        "https://textures.minecraft.net/texture/7f9bc035cdc80f1ab5e1198f29f3ad3fdd2b42d9a69aeb64de990681800b98dc"
      )
    );
    values.put(
      "en_us",
      new LanguageOption(
        "English (US)",
        "https://textures.minecraft.net/texture/cd91456877f54bf1ace251e4cee40dba597d2cc40362cb8f4ed711e50b0be5b3"
      )
    );
    values.put(
      "ja_jp",
      new LanguageOption(
        "日本語（日本）",
        "https://textures.minecraft.net/texture/d640ae466162a47d3ee33c4076df1cab96f11860f07edb1f0832c525a9e33323"
      )
    );
    values.put(
      "ko_kr",
      new LanguageOption(
        "한국어 (대한민국)",
        "https://textures.minecraft.net/texture/ca12913d7df640d18bcc7a45a8172c68ffa04756e84c6f0a2eda3da45e00dadd"
      )
    );
    values.put(
      "es_es",
      new LanguageOption(
        "Español (España)",
        "https://textures.minecraft.net/texture/c2d730b6dda16b584783b63d082a80049b5fa70228aba4ae884c2c1fc0c3a8bc"
      )
    );
    return Collections.unmodifiableMap(values);
  }

  private record LanguageOption(String nativeName, String texture) {}
}
