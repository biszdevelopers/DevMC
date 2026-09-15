package dev.bisz.enchants.items;

import dev.bisz.items.Ability;
import dev.bisz.items.AbilityUsageMethod;
import dev.bisz.items.EnchantmentId;
import dev.bisz.items.Quality;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Faster attack, draw, and throw definition. */
public final class NimbleEnchantment extends EnchantsEnchantment {
  public static final EnchantmentId ID = EnchantmentId.of("enchants", "nimble");
  public static final Ability SHORTBOW = Ability.builder("ability.enchants.shortbow")
    .name("Shortbow")
    .description("Instantly fires arrows on left click.")
    .quality(Quality.COMMON)
    .usage(AbilityUsageMethod.LEFT_CLICK)
    .cooldownSeconds(.5D)
    .build();

  public NimbleEnchantment() { super("nimble"); }
  @Override protected String fallbackDescription(int level) { return "Makes attacks, draws, and throws %d%% faster."; }
  @Override protected Object[] descriptionArguments(int level) { return new Object[] { level * 10 }; }
  @Override public String displayDescription(Player viewer, dev.bisz.items.EnchantmentData data, Material material) {
    if (material == Material.BOW) return translate(viewer, "enchantment.enchants.nimble.shortbow.description",
      "Transforms this bow into a Shortbow that instantly fires arrows on left click.");
    return super.displayDescription(viewer, data, material);
  }
}
