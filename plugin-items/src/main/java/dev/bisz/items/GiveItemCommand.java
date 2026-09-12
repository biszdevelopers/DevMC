/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  dev.bisz.commands.DevCommand
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 */
package dev.bisz.items;

import dev.bisz.commands.DevCommand;
import dev.bisz.items.DevItem;
import dev.bisz.items.DevItemStack;
import dev.bisz.items.ItemFactory;
import dev.bisz.items.ItemId;
import dev.bisz.items.ItemRegistry;
import dev.bisz.items.ItemTranslations;
import dev.bisz.items.MetadataPair;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class GiveItemCommand extends DevCommand {

  private final ItemFactory factory;
  private final ItemRegistry registry;

  GiveItemCommand(ItemFactory factory, ItemRegistry registry) {
    super("giveitem", "items.give");
    this.factory = factory;
    this.registry = registry;
    this.setUsage("/giveitem <player> <namespace:item> [amount]");
  }

  protected boolean executeCommand(
    CommandSender sender,
    String label,
    String[] args
  ) {
    if (args.length < 2 || args.length > 3) {
      sender.sendMessage(
        ItemTranslations.forSender(
          sender,
          "command.giveitem.usage",
          "Usage: /giveitem <player> <namespace:item> [amount]",
          new Object[0]
        )
      );
      return true;
    }
    Player target = Bukkit.getPlayerExact((String) args[0]);
    if (target == null) {
      sender.sendMessage(
        ItemTranslations.forSender(
          sender,
          "command.giveitem.player_not_found",
          "Player not found: %s",
          args[0]
        )
      );
      return true;
    }
    try {
      int amount;
      ItemId id = ItemId.parse(args[1]);
      DevItem definition = this.registry.get(id).orElse(null);
      if (definition == null) {
        sender.sendMessage(
          ItemTranslations.forSender(
            sender,
            "command.giveitem.unknown_item",
            "Unknown item: %s",
            args[1]
          )
        );
        return true;
      }
      try {
        amount = args.length == 3 ? Integer.parseInt(args[2]) : 1;
      } catch (NumberFormatException exception) {
        amount = -1;
      }
      if (amount < 1 || amount > definition.properties().maximumStackSize()) {
        sender.sendMessage(
          ItemTranslations.forSender(
            sender,
            "command.giveitem.invalid_amount",
            "Invalid amount '%s'; expected 1-%d.",
            args.length == 3 ? args[2] : Integer.toString(amount),
            definition.properties().maximumStackSize()
          )
        );
        return true;
      }
      DevItemStack stack = this.factory.create(id, amount, new MetadataPair[0]);
      stack.render(target);
      target.getInventory().addItem(new ItemStack[] { stack.bukkitStack() });
      sender.sendMessage(
        ItemTranslations.forSender(
          sender,
          "command.giveitem.success",
          "Gave %d \u00d7 %s to %s.",
          amount,
          id,
          target.getName()
        )
      );
    } catch (IllegalArgumentException exception) {
      sender.sendMessage(
        ItemTranslations.forSender(
          sender,
          "command.giveitem.invalid_item",
          "Invalid item id: %s",
          args[1]
        )
      );
    }
    return true;
  }

  protected List<String> complete(
    CommandSender sender,
    String alias,
    String[] args
  ) {
    if (args.length == 1) {
      return Bukkit.getOnlinePlayers()
        .stream()
        .map(Player::getName)
        .filter(name ->
          name
            .toLowerCase(Locale.ROOT)
            .startsWith(args[0].toLowerCase(Locale.ROOT))
        )
        .sorted()
        .toList();
    }
    if (args.length == 2) {
      return this.registry.values()
        .stream()
        .map(item -> item.id().toString())
        .filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT)))
        .sorted(Comparator.naturalOrder())
        .collect(Collectors.toList());
    }
    return List.of();
  }

  protected String permissionDeniedMessage(CommandSender sender) {
    return ItemTranslations.forSender(
      sender,
      "command.giveitem.permission_denied",
      "You do not have permission to use /giveitem.",
      new Object[0]
    );
  }

  protected String executionFailureMessage(CommandSender sender) {
    return ItemTranslations.forSender(
      sender,
      "command.giveitem.failure",
      "The item command could not be completed. Check the server log.",
      new Object[0]
    );
  }
}
