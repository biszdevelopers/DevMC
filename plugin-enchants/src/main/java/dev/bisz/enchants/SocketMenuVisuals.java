package dev.bisz.enchants;

import dev.bisz.enchants.items.SocketDisplayRenderer;
import dev.bisz.items.DevEnchantment;
import dev.bisz.items.DevItemStack;
import dev.bisz.items.EnchantmentData;
import dev.bisz.items.EnchantmentDisplay;
import dev.bisz.items.ItemsPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Shared socket-grid rendering for the enchanting table and the anvil and
 * grindstone stations: category-colored dyes represent empty sockets and ender
 * eyes represent filled ones.
 */
final class SocketMenuVisuals {
  static final int[] SOCKET_SLOTS = { 29, 30, 31, 32, 33, 38, 39, 40, 41, 42 };
  static final int[] BLACK_SLOTS = {
    0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 51, 52, 53,
  };

  private SocketMenuVisuals() {}

  static int gridPosition(int index, int socketCount) {
    int topCount = Math.min(5, socketCount);
    if (index < topCount) return SOCKET_SLOTS[(5 - topCount) / 2 + index];
    int bottomCount = socketCount - topCount;
    return SOCKET_SLOTS[5 + (5 - bottomCount) / 2 + index - topCount];
  }

  /** Returns the represented socket index, or null for an unused grid cell. */
  static Integer socketAt(ItemStack input, int gridSlot) {
    if (input == null) return null;
    try {
      DevItemStack wrapped = ItemsPlugin.instance().factory().wrap(input);
      if (!(wrapped.definition() instanceof SocketedVanillaItem definition)) return null;
      List<EnchantmentSlot> sockets = definition.sockets();
      for (int index = 0; index < sockets.size(); index++)
        if (gridPosition(index, sockets.size()) == gridSlot) return index;
    } catch (RuntimeException ignored) {}
    return null;
  }

  static boolean socketIsFree(ItemStack input, int socket) {
    if (input == null) return false;
    try {
      DevItemStack wrapped = ItemsPlugin.instance().factory().wrap(input);
      return wrapped.definition() instanceof SocketedVanillaItem definition
        && socket >= 0 && definition.freeSlots(wrapped).stream().anyMatch(slot -> slot.index() == socket);
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  static Material dye(EnchantmentCategory category) {
    return switch (category) {
      case FATALITY -> Material.RED_DYE;
      case PROWESS -> Material.MAGENTA_DYE;
      case PROTECTION -> Material.LIME_DYE;
      case MOBILITY -> Material.LIGHT_BLUE_DYE;
      case TIDE -> Material.BLUE_DYE;
      case HARVESTING -> Material.ORANGE_DYE;
      case SUSTAINABILITY -> Material.YELLOW_DYE;
      case UNIVERSAL -> Material.WHITE_DYE;
    };
  }

  static ItemStack named(Material material, String name, List<String> lore) {
    ItemStack icon = new ItemStack(material);
    ItemMeta meta = icon.getItemMeta();
    meta.setDisplayName(name);
    meta.setLore(lore);
    icon.setItemMeta(meta);
    return icon;
  }

  static ItemStack emptySocket(EnchantmentSlot slot, Player viewer, List<String> actionLore) {
    return named(dye(slot.category()),
      SocketDisplayRenderer.empty(slot.category().icon(), viewer), actionLore);
  }

  static ItemStack filledSocket(EnchantmentSlot slot, Map.Entry<DevEnchantment, EnchantmentData> filled,
      Material material, Player viewer, List<String> actionLore) {
    ArrayList<String> lore = new ArrayList<>(
      SocketedVanillaItem.isMending(filled.getKey())
        ? SocketedVanillaItem.renderMendingDescription(viewer)
        : EnchantmentDisplay.renderDescription(filled.getKey(), filled.getValue(), viewer, material));
    if (!lore.isEmpty()) lore.add("");
    lore.addAll(actionLore);
    EnchantmentCategory display = slot.filledCategory(filled.getKey());
    ItemStack iconStack = named(Material.ENDER_EYE,
      SocketDisplayRenderer.filled(display.color(), display.icon(), filled, viewer), lore);
    iconStack.setAmount(Math.max(1, Math.min(64, filled.getValue().level())));
    return iconStack;
  }

  static ItemStack placeholderDye() {
    return named(Material.GRAY_DYE, " ", List.of());
  }

  static ItemStack blackFiller() {
    return named(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
  }

  static ItemStack grayFiller() {
    return named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
  }
}
