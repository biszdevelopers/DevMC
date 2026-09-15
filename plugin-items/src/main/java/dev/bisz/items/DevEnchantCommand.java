package dev.bisz.items;

import dev.bisz.commands.DevCommand;
import dev.bisz.players.Rank;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Administrative escape hatch for applying vanilla or custom DevEnchantments. */
final class DevEnchantCommand extends DevCommand {
  private static final String USAGE = "/devenchant <namespace:id> <level> [namespace:key=value ...]";
  private final ItemFactory factory;
  private final EnchantmentRegistry registry;
  DevEnchantCommand(ItemFactory factory, EnchantmentRegistry registry) {
    super("devenchant", "items.debug");
    this.factory = factory; this.registry = registry;
    setUsage(USAGE); requireRank(Rank.ADMIN); requireOperator();
  }
  @Override protected boolean executeCommand(CommandSender sender, String label, String[] args) {
    if (!(sender instanceof Player player)) { sender.sendMessage(message(sender, "player_only", "This command can only be used by a player.")); return true; }
    if (args.length < 2) { sender.sendMessage(message(sender, "usage", "Usage: " + USAGE)); return true; }
    EnchantmentId id;
    int level;
    try { id = EnchantmentId.parse(args[0]); level = Integer.parseInt(args[1]); } catch (IllegalArgumentException exception) { sender.sendMessage(message(sender, "invalid", "Invalid enchantment id or level.")); return true; }
    if (level < 1 || level > 3999) { sender.sendMessage(message(sender, "invalid_level", "Level must be between 1 and 3999.")); return true; }
    DevEnchantment enchantment = registry.get(id).orElse(null);
    if (enchantment == null) { sender.sendMessage(message(sender, "unknown", "Unknown enchantment: %s", id)); return true; }
    ItemStack held = player.getInventory().getItemInMainHand();
    if (held == null || held.getType().isAir()) { sender.sendMessage(message(sender, "empty_hand", "Hold an item to enchant.")); return true; }
    Map<NamespacedKey, Object> metadata;
    try {
      metadata = parseMetadata(args, 2);
    } catch (IllegalArgumentException exception) {
      sender.sendMessage(message(sender, "invalid_metadata",
        "Invalid metadata '%s'. Use namespace:key=value with a true/false or whole-number value.", exception.getMessage()));
      return true;
    }
    try {
      DevItemStack stack = factory.wrap(held); stack.enchant(enchantment, level);
      metadata.forEach((key, value) -> stack.setEnchantmentMetadata(enchantment, key, value));
      stack.render(player);
      player.getInventory().setItemInMainHand(stack.bukkitStack());
      sender.sendMessage(message(sender, "success", "Applied %s %s.", id, RomanNumerals.format(level)));
    } catch (RuntimeException exception) {
      sender.sendMessage(message(sender, "failure", "The enchantment could not be applied. Check the server log."));
    }
    return true;
  }
  @Override protected List<String> complete(CommandSender sender, String alias, String[] args) {
    if (args.length == 1) {
      String prefix = args[0].toLowerCase(Locale.ROOT);
      return registry.values().stream().map(enchantment -> enchantment.id().toString()).filter(id -> id.startsWith(prefix)).sorted(Comparator.naturalOrder()).toList();
    }
    if (args.length < 3 || !(sender instanceof Player player)) return List.of();
    String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
    if (prefix.contains("=")) {
      int separator = prefix.indexOf('=');
      String keyPrefix = prefix.substring(0, separator + 1);
      String valuePrefix = prefix.substring(separator + 1);
      return List.of("true", "false").stream().filter(value -> value.startsWith(valuePrefix)).map(value -> keyPrefix + value).toList();
    }
    DevEnchantment enchantment;
    try { enchantment = registry.get(EnchantmentId.parse(args[0])).orElse(null); }
    catch (IllegalArgumentException exception) { return List.of(); }
    if (enchantment == null) return List.of();
    ItemStack held = player.getInventory().getItemInMainHand();
    if (held == null || held.getType().isAir()) return List.of();
    try {
      return factory.wrap(held).enchantmentMetadata(enchantment).keySet().stream()
        .map(key -> key + "=").filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    } catch (RuntimeException ignored) { return List.of(); }
  }
  /** Parses optional namespace:key=value pairs starting at {@code start}. */
  static Map<NamespacedKey, Object> parseMetadata(String[] args, int start) {
    LinkedHashMap<NamespacedKey, Object> metadata = new LinkedHashMap<>();
    for (int index = start; index < args.length; index++) {
      String token = args[index];
      int separator = token.indexOf('=');
      if (separator <= 0 || separator == token.length() - 1) throw new IllegalArgumentException(token);
      String keyToken = token.substring(0, separator);
      int namespaceSeparator = keyToken.indexOf(':');
      if (namespaceSeparator <= 0 || namespaceSeparator == keyToken.length() - 1) throw new IllegalArgumentException(token);
      NamespacedKey key = NamespacedKey.fromString(keyToken);
      if (key == null) throw new IllegalArgumentException(token);
      metadata.put(key, parseMetadataValue(token.substring(separator + 1)));
    }
    return metadata;
  }
  /** Parses a Boolean or whole-number metadata value, allowing underscores as digit separators. */
  static Object parseMetadataValue(String raw) {
    if (raw.equalsIgnoreCase("true")) return Boolean.TRUE;
    if (raw.equalsIgnoreCase("false")) return Boolean.FALSE;
    try { return Integer.valueOf(raw.replace("_", "")); }
    catch (NumberFormatException exception) { throw new IllegalArgumentException(raw); }
  }
  @Override protected String permissionDeniedMessage(CommandSender sender) { return message(sender, "permission_denied", "You do not have permission to use this debug command."); }
  @Override protected String executionFailureMessage(CommandSender sender) { return message(sender, "failure", "The enchantment command failed. Check the server log."); }
  private String message(CommandSender sender, String suffix, String fallback, Object... arguments) { return ItemTranslations.forSender(sender, "command.devenchant." + suffix, fallback, arguments); }
}
