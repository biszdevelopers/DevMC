package dev.bisz.items;

import dev.bisz.commands.DevCommand;
import dev.bisz.players.Rank;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ForceRenderCommand extends DevCommand {

  private final ItemFactory factory;

  ForceRenderCommand(ItemFactory factory) {
    super("forcerender", "items.debug");
    this.factory = factory;
    this.setUsage("/forcerender [player]");
    this.requireRank(Rank.ADMIN);
    this.requireOperator();
  }

  @Override
  protected boolean executeCommand(
    CommandSender sender,
    String label,
    String[] args
  ) {
    if (args.length > 1) {
      sender.sendMessage(
        message(sender, "usage", "Usage: /forcerender [player]")
      );
      return true;
    }
    Player target = args.length == 1
      ? Bukkit.getPlayerExact(args[0])
      : sender instanceof Player player ? player : null;
    if (target == null) {
      sender.sendMessage(
        message(
          sender,
          "player_not_found",
          "Player not found. Specify an online player."
        )
      );
      return true;
    }
    factory.forceRenderInventory(target);
    sender.sendMessage(
      message(
        sender,
        "success",
        "Re-rendered every item in %s's inventory.",
        target.getName()
      )
    );
    return true;
  }

  @Override
  protected List<String> complete(
    CommandSender sender,
    String alias,
    String[] args
  ) {
    if (args.length != 1) return List.of();
    String prefix = args[0].toLowerCase(Locale.ROOT);
    return Bukkit.getOnlinePlayers()
      .stream()
      .map(Player::getName)
      .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
      .sorted()
      .toList();
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
      "The force-render command failed. Check the server log."
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
      "command.forcerender." + suffix,
      fallback,
      args
    );
  }
}
