package dev.bisz.items;

import dev.bisz.commands.DevCommand;
import dev.bisz.players.Rank;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class GiveRandomThingsCommand extends DevCommand {

  private static final int MAX_STACKS = 256;
  private static final List<String> OPTIONS = List.of(
    "all",
    "all:devitem",
    "all:vanilla",
    "custom:devitem",
    "custom:vanilla"
  );
  private final ItemFactory factory;
  private final ItemRegistry registry;

  GiveRandomThingsCommand(ItemFactory factory, ItemRegistry registry) {
    super("giverandomthings", "items.debug");
    this.factory = factory;
    this.registry = registry;
    this.setUsage("/giverandomthings <count> [option]");
    this.requireRank(Rank.ADMIN);
    this.requireOperator();
  }

  @Override
  protected boolean executeCommand(
    CommandSender sender,
    String label,
    String[] args
  ) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(
        message(
          sender,
          "player_only",
          "This command can only be used by a player."
        )
      );
      return true;
    }
    if (args.length < 1 || args.length > 2) {
      sender.sendMessage(
        message(sender, "usage", "Usage: /giverandomthings <count> [option]")
      );
      return true;
    }

    int count;
    try {
      count = Integer.parseInt(args[0]);
    } catch (NumberFormatException ignored) {
      count = -1;
    }
    if (count < 1 || count > MAX_STACKS) {
      sender.sendMessage(
        message(
          sender,
          "invalid_count",
          "Count must be between 1 and %d.",
          MAX_STACKS
        )
      );
      return true;
    }

    String option = args.length == 2 ? args[1].toLowerCase(Locale.ROOT) : "all";
    if (!OPTIONS.contains(option)) {
      sender.sendMessage(
        message(sender, "invalid_option", "Unknown option: %s", option)
      );
      return true;
    }
    List<DevItem> candidates = candidates(option);
    if (candidates.isEmpty()) {
      sender.sendMessage(
        message(sender, "empty", "No items match option %s.", option)
      );
      return true;
    }

    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int i = 0; i < count; i++) {
      DevItem definition = candidates.get(random.nextInt(candidates.size()));
      int amount = random.nextInt(
        1,
        definition.properties().maximumStackSize() + 1
      );
      DevItemStack created = factory.create(definition.id(), amount);
      created.render(player);
      ItemStack item = created.bukkitStack();
      Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
      leftovers
        .values()
        .forEach(leftover ->
          player.getWorld().dropItemNaturally(player.getLocation(), leftover)
        );
    }
    sender.sendMessage(
      message(
        sender,
        "success",
        "Generated %d random item stacks (%s).",
        count,
        option
      )
    );
    return true;
  }

  private List<DevItem> candidates(String option) {
    return registry
      .values()
      .stream()
      .filter(item ->
        switch (option) {
          case "all", "all:devitem" -> true;
          case "all:vanilla" -> item.vanilla();
          case "custom:devitem" -> !item.vanilla();
          case "custom:vanilla" -> item.vanilla() &&
          registry.hasVanillaOverrides(item.id());
          default -> false;
        }
      )
      .toList();
  }

  @Override
  protected List<String> complete(
    CommandSender sender,
    String alias,
    String[] args
  ) {
    return args.length == 2 ? OPTIONS : List.of();
  }

  @Override
  protected String permissionDeniedMessage(CommandSender sender) {
    return message(
      sender,
      "permission_denied",
      "You do not have permission to use this debug command."
    );
  }

  @Override
  protected String executionFailureMessage(CommandSender sender) {
    return message(
      sender,
      "failure",
      "The random-item command failed. Check the server log."
    );
  }

  private String message(
    CommandSender sender,
    String suffix,
    String fallback,
    Object... args
  ) {
    return ItemTranslations.forSender(
      sender,
      "command.giverandomthings." + suffix,
      fallback,
      args
    );
  }
}
