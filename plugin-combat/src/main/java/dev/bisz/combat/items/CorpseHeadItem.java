package dev.bisz.combat.items;

import dev.bisz.items.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import dev.bisz.players.locales.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class CorpseHeadItem extends CustomItem {

  public static final ItemId ID = ItemId.of("combat", "corpse_head");

  public CorpseHeadItem() {
    super(
      ID,
      ItemProperties.builder(Material.PLAYER_HEAD)
        .maximumStackSize(1)
        .quality(Quality.UNCOMMON)
        .placeable(false)
        .metadata("owner", ItemDataType.STRING, "")
        .metadata("killer", ItemDataType.STRING, "")
        .metadata("cause", ItemDataType.STRING, "")
        .metadata("death_time", ItemDataType.LONG, 0L)
        .build()
    );
  }

  @Override
  protected String renderName(DevItemStack s, Player v) {
    return translate(
      v,
      "combat.item.corpse_head.name",
      "%s's Head",
      s.metadata("owner").orElse("Unknown")
    );
  }

  private static DateTimeFormatter FORMATTER(Player v) {
    return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
  }


  public static String formatMillis(long epochMillis, Player v) {
    LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(epochMillis),
            ZoneId.systemDefault()
    );
    return dateTime.format(FORMATTER(v));
  }

  @Override
  protected List<String> renderLore(DevItemStack s, Player v) {

    long a = s.metadata("death_time")
            .map(Long.class::cast)
            .orElse(0L);
    String cause = String.valueOf(s.metadata("cause").orElse("unknown"));
    String causeId = cause.isBlank()
      ? "unknown"
      : cause.toLowerCase(java.util.Locale.ROOT);
    if (causeId.startsWith("player.death.")) {
      causeId = causeId.substring("player.death.".length());
    }
    String translatedCause = translate(
      v,
      "player.death." + causeId,
      humanize(causeId)
    );

    return List.of(
            translate(
                    v,
                    "combat.item.corpse_head.description",
                    ""
            ),
      " ",
      translate(
        v,
        "combat.item.corpse_head.cause",
        "Cause: %s",
        translatedCause
      ),
      translate(
        v,
        "combat.item.corpse_head.killer",
        "Killer: %s",
        s.metadata("killer").orElse(translate(v, "combat.item.corpse_head.none", "none"))
      ),
            translate(v, "combat.item.corpse_head.death_time", "TOD: %s", formatMillis(a, v))
    );
  }

  private static String humanize(String value) {
    String spaced = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    return spaced.isEmpty()
      ? "Unknown"
      : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
  }
}
