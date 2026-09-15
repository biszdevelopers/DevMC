package dev.bisz.enchants;

import dev.bisz.commands.DevCommand;
import dev.bisz.items.DevEnchantment;
import dev.bisz.items.DevItemStack;
import dev.bisz.items.EnchantmentData;
import dev.bisz.items.ItemsPlugin;
import dev.bisz.items.RomanNumerals;
import dev.bisz.players.Rank;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Administrative command for the trueMC socket system. */
final class EnchantsCommand extends DevCommand {
  private static final String USAGE = "/enchants <give|info|xp|tooltips|reload>";

  private final EnchantsPlugin plugin;

  EnchantsCommand(EnchantsPlugin plugin) {
    super("enchants", "enchants.admin");
    this.plugin = plugin;
    setUsage(USAGE);
    requireRank(Rank.ADMIN);
    requireOperator();
  }

  @Override protected boolean executeCommand(CommandSender sender, String label, String[] args) {
    if (args.length == 0) {
      sender.sendMessage("Usage: " + USAGE);
      return true;
    }
    switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
      case "give" -> give(sender, args);
      case "info" -> info(sender);
      case "xp" -> xp(sender, args);
      case "tooltips" -> tooltips(sender, args);
      case "reload" -> {
        plugin.reload();
        sender.sendMessage("§atrueMC configuration reloaded.");
      }
      default -> sender.sendMessage("Usage: " + USAGE);
    }
    return true;
  }

  private void give(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can use this command.");
      return;
    }
    if (args.length < 2) {
      sender.sendMessage("Usage: /enchants give <material>");
      return;
    }
    Material material = Material.matchMaterial(args[1]);
    if (material == null) {
      sender.sendMessage("§cUnknown material: " + args[1]);
      return;
    }
    if (!SocketedVanillaItem.supports(material)) {
      sender.sendMessage("§c" + material.name() + " has no socket layout.");
      return;
    }
    ItemStack item = new ItemStack(material);
    DevItemStack stack = ItemsPlugin.instance().factory().wrap(item);
    int sockets = stack.definition() instanceof SocketedVanillaItem definition ? definition.sockets().size() : 0;
    stack.render(player);
    player.getInventory().addItem(stack.bukkitStack());
    sender.sendMessage("§aGave you " + material.name().toLowerCase(java.util.Locale.ROOT) + " (" + sockets + " sockets).");
  }

  private void info(CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can use this command.");
      return;
    }
    ItemStack held = player.getInventory().getItemInMainHand();
    if (held.getType().isAir()) {
      sender.sendMessage("§cHold an item to inspect it.");
      return;
    }
    try {
      DevItemStack stack = ItemsPlugin.instance().factory().wrap(held);
      if (!(stack.definition() instanceof SocketedVanillaItem definition)) {
        sender.sendMessage("§cThis item has no socket layout.");
        return;
      }
      Map<Integer, Map.Entry<DevEnchantment, EnchantmentData>> assigned = definition.normalize(stack);
      sender.sendMessage("§f" + held.getType().name() + " §7- " + definition.sockets().size() + " sockets:");
      for (EnchantmentSlot socket : definition.sockets()) {
        Map.Entry<DevEnchantment, EnchantmentData> filled = assigned.get(socket.index());
        String suffix = filled == null ? "§8empty"
          : filled.getKey().properties().quality().colorCode() + filled.getKey().displayName(player)
            + " " + RomanNumerals.format(filled.getValue().level());
        EnchantmentCategory display = filled == null ? socket.category() : socket.filledCategory(filled.getKey());
        sender.sendMessage("  " + display.color() + "[" + display.icon() + " "
          + display.displayName(player) + "§r] §7> " + suffix);
      }
      sender.sendMessage("§7Cost: §e" + plugin.enchantingCosts().materialCost(held.getType())
        + "§7 levels | Refund: §e" + plugin.enchantingCosts().refund(held.getType()) + "§7 levels");
    } catch (RuntimeException exception) {
      sender.sendMessage("§cCould not read socket data for this item.");
    }
  }

  private void xp(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can use this command.");
      return;
    }
    if (args.length < 2) {
      status(sender, player);
      return;
    }
    switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
      case "add" -> {
        if (args.length < 3) { sender.sendMessage("Usage: /enchants xp add <amount>"); return; }
        int amount = parse(args[2]);
        LinearExperience.addPoints(player, amount);
        sender.sendMessage("§aAdded " + amount + " XP.");
        status(sender, player);
      }
      case "set" -> {
        if (args.length < 3) { sender.sendMessage("Usage: /enchants xp set <amount>"); return; }
        LinearExperience.setTotalPoints(player, parse(args[2]));
        sender.sendMessage("§aSet XP.");
        status(sender, player);
      }
      case "get" -> status(sender, player);
      default -> sender.sendMessage("Usage: /enchants xp <add|set|get> [amount]");
    }
  }

  private void status(CommandSender sender, Player player) {
    int perLevel = LinearExperience.pointsPerLevel();
    int current = LinearExperience.totalPoints(player) % perLevel;
    sender.sendMessage("§7XP: §e" + LinearExperience.totalPoints(player) + "§7 | Level: §e" + player.getLevel()
      + "§7 | Progress: §e" + current + "/" + perLevel);
  }

  private void tooltips(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can use this command.");
      return;
    }
    if (args.length >= 2) {
      switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
        case "collapse", "off" -> TooltipPreferences.set(player, true);
        case "expand", "on" -> TooltipPreferences.set(player, false);
        case "toggle" -> TooltipPreferences.toggle(player);
        default -> {
          sender.sendMessage("§7Usage: /enchants tooltips <collapse|expand|toggle>");
          return;
        }
      }
    } else {
      TooltipPreferences.toggle(player);
    }
    sender.sendMessage("§7Socket tooltips: §f"
      + (TooltipPreferences.collapsed(player) ? "collapsed" : "expanded"));
  }

  private static int parse(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  @Override protected List<String> complete(CommandSender sender, String alias, String[] args) {
    if (args.length == 1) return filter(Arrays.asList("give", "info", "xp", "tooltips", "reload"), args[0]);
    if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
      List<String> materials = new ArrayList<>();
      for (Material material : Material.values()) {
        if (SocketedVanillaItem.supports(material)) materials.add(material.name().toLowerCase(java.util.Locale.ROOT));
      }
      return filter(materials, args[1]);
    }
    if (args.length == 2 && args[0].equalsIgnoreCase("xp")) return filter(Arrays.asList("add", "set", "get"), args[1]);
    if (args.length == 2 && args[0].equalsIgnoreCase("tooltips")) return filter(Arrays.asList("collapse", "expand", "toggle"), args[1]);
    return List.of();
  }

  private static List<String> filter(List<String> candidates, String prefix) {
    String lower = prefix.toLowerCase(java.util.Locale.ROOT);
    return candidates.stream().filter(candidate -> candidate.startsWith(lower)).toList();
  }
}
