package dev.bisz.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.entity.Player;

/** Shared localized lore renderer for declarative abilities. */
public final class AbilityDisplay {
  private AbilityDisplay() {}

  public static List<String> render(List<Ability> abilities, Player viewer) {
    List<Ability> definitions = List.copyOf(Objects.requireNonNull(abilities, "abilities"));
    ArrayList<String> lines = new ArrayList<>();
    for (int index = 0; index < definitions.size(); index++) {
      Ability ability = Objects.requireNonNull(definitions.get(index), "ability");
      if (index > 0) lines.add("");
      lines.add(ability.quality().colorCode() + ability.displayName(viewer));
      lines.addAll(EnchantmentDisplay.wrapGray(ability.displayDescription(viewer), viewer));
      if (ability.usageMethod().passive()) {
        lines.add(ItemTranslations.translate(
          ItemTranslations.language(viewer), "ability.activates_every",
          "  §8Activates every §e%.1f seconds §8(%s)",
          ability.activationIntervalSeconds(), ability.usageMethod().displayName(viewer)
        ));
      } else {
        if (ability.cooldownSeconds() == 0D) {
          lines.add(ItemTranslations.translate(
            ItemTranslations.language(viewer), "ability.cooldown.instant",
            "  §8Cooldown: §aInstant!"
          ));
        } else {
          lines.add(ItemTranslations.translate(
            ItemTranslations.language(viewer), "ability.cooldown",
            "  §8Cooldown: §a%.1fs", ability.cooldownSeconds()
          ));
        }
        lines.add(ItemTranslations.translate(
          ItemTranslations.language(viewer), "ability.use_by",
          "  §8Use by §e%s", ability.usageMethod().displayName(viewer)
        ));
      }
    }
    return List.copyOf(lines);
  }
}
