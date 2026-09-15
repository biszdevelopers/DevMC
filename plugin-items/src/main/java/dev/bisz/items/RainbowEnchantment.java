package dev.bisz.items;

import org.bukkit.entity.Player;

/** Built-in visual test enchantment with no gameplay behavior. */
public final class RainbowEnchantment extends CustomEnchantment {
  private static final String[] COLORS = { "§c", "§6", "§e", "§a", "§b", "§d" };

  public RainbowEnchantment() {
    super(EnchantmentId.of("items", "rainbow"), EnchantmentProperties.builder().quality(Quality.EPIC).build());
  }

  @Override protected String renderLore(Player viewer, int level) {
    String text = renderName(viewer) + " " + RomanNumerals.format(level);
    StringBuilder rendered = new StringBuilder();
    int color = 0;
    for (int index = 0; index < text.length(); index++) {
      rendered.append(COLORS[color++ % COLORS.length]).append(text.charAt(index));
    }
    return rendered.toString();
  }

  @Override protected String renderDescription(Player viewer, EnchantmentData data) {
    return translate(viewer, "enchantment.items.rainbow.description", "Shimmers through every color of the rainbow.");
  }
}
