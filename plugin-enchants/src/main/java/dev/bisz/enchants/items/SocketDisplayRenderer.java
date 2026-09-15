package dev.bisz.enchants.items;

import dev.bisz.items.DevEnchantment;
import dev.bisz.items.EnchantmentData;
import dev.bisz.items.RomanNumerals;
import dev.bisz.players.locales.Locale;
import java.util.Map;
import org.bukkit.entity.Player;

/** Shared lore rendering for typed equipment sockets and the book universal socket. */
public final class SocketDisplayRenderer {
  private SocketDisplayRenderer() {}


  public static String empty(String color, String icon, Player viewer) {

    return Locale.get(viewer, "enchants.socket.empty", color, icon, color);
  }

  /** Menu label: typed icons/brackets are neutral; Universal remains gray while empty. */
  public static String selectableEmpty(String icon, boolean universal, Player viewer) {
    return empty("§7", icon, viewer);
  }

  public static String filled(String color, String icon, Map.Entry<DevEnchantment, EnchantmentData> entry, Player viewer) {
    String qualityName = entry.getKey().properties().quality().colorCode() + entry.getKey().displayName(viewer);
    return Locale.get(viewer, "enchants.socket.filled", color, icon, qualityName,
      RomanNumerals.format(entry.getValue().level()), color);
  }
}
