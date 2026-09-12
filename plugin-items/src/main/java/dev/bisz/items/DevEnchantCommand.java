package dev.bisz.items;

import dev.bisz.commands.DevCommand;
import dev.bisz.players.Rank;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Administrative escape hatch for applying vanilla or custom DevEnchantments. */
final class DevEnchantCommand extends DevCommand {
  private final ItemFactory factory;
  private final EnchantmentRegistry registry;
  DevEnchantCommand(ItemFactory factory, EnchantmentRegistry registry) {
    super("devenchant", "items.debug");
    this.factory = factory; this.registry = registry;
    setUsage("/devenchant <namespace:id> <level>"); requireRank(Rank.ADMIN); requireOperator();
  }
  @Override protected boolean executeCommand(CommandSender sender, String label, String[] args) {
    if (!(sender instanceof Player player)) { sender.sendMessage(message(sender, "player_only", "This command can only be used by a player.")); return true; }
    if (args.length != 2) { sender.sendMessage(message(sender, "usage", "Usage: /devenchant <namespace:id> <level>")); return true; }
    EnchantmentId id;
    int level;
    try { id = EnchantmentId.parse(args[0]); level = Integer.parseInt(args[1]); } catch (IllegalArgumentException exception) { sender.sendMessage(message(sender, "invalid", "Invalid enchantment id or level.")); return true; }
    if (level < 1 || level > 3999) { sender.sendMessage(message(sender, "invalid_level", "Level must be between 1 and 3999.")); return true; }
    DevEnchantment enchantment = registry.get(id).orElse(null);
    if (enchantment == null) { sender.sendMessage(message(sender, "unknown", "Unknown enchantment: %s", id)); return true; }
    ItemStack held = player.getInventory().getItemInMainHand();
    if (held == null || held.getType().isAir()) { sender.sendMessage(message(sender, "empty_hand", "Hold an item to enchant.")); return true; }
    try {
      DevItemStack stack = factory.wrap(held); stack.enchant(enchantment, level); stack.render(player);
      player.getInventory().setItemInMainHand(stack.bukkitStack());
      sender.sendMessage(message(sender, "success", "Applied %s %s.", id, RomanNumerals.format(level)));
    } catch (RuntimeException exception) {
      sender.sendMessage(message(sender, "failure", "The enchantment could not be applied. Check the server log."));
    }
    return true;
  }
  @Override protected List<String> complete(CommandSender sender, String alias, String[] args) {
    if (args.length != 1) return List.of(); String prefix = args[0].toLowerCase(Locale.ROOT);
    return registry.values().stream().map(enchantment -> enchantment.id().toString()).filter(id -> id.startsWith(prefix)).sorted(Comparator.naturalOrder()).toList();
  }
  @Override protected String permissionDeniedMessage(CommandSender sender) { return message(sender, "permission_denied", "You do not have permission to use this debug command."); }
  @Override protected String executionFailureMessage(CommandSender sender) { return message(sender, "failure", "The enchantment command failed. Check the server log."); }
  private String message(CommandSender sender, String suffix, String fallback, Object... arguments) { return ItemTranslations.forSender(sender, "command.devenchant." + suffix, fallback, arguments); }
}
