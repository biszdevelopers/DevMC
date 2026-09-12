package dev.bisz.currency;

import dev.bisz.commands.CommandArgumentHint;
import dev.bisz.commands.DevCommand;
import dev.bisz.commands.MathExpressions;
import dev.bisz.players.Profile;
import dev.bisz.players.Rank;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class NitsCommand extends DevCommand {

  private static final String PURSE_METADATA_KEY = "purse";
  private static final List<String> OPERATIONS = List.of("give", "remove");

  private final JavaPlugin plugin;
  private final CurrencyManager currencyManager;

  NitsCommand(JavaPlugin plugin, CurrencyManager currencyManager) {
    super("nits", "currency.nits.debug");
    requireRank(Rank.ADMIN);
    requireOperator();
    this.plugin = plugin;
    this.currencyManager = currencyManager;
    this.setUsage("/nits <give|remove> <amount>");
  }

  @Override
  protected boolean executeCommand(
    CommandSender sender,
    String label,
    String[] arguments
  ) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can use /nits.");
      return true;
    }
    if (arguments.length < 2) {
      player.sendMessage("Usage: /nits <give|remove> <amount>");
      return true;
    }

    CurrencyManager.NitsOperation operation;
    long amount;
    try {
      operation = CurrencyManager.NitsOperation.fromCommandArgument(
        arguments[0]
      );
      amount = MathExpressions.evaluateInteger(
        String.join("", Arrays.copyOfRange(arguments, 1, arguments.length))
      );
      if (amount <= 0L) {
        throw new IllegalArgumentException("amount must be positive");
      }
    } catch (IllegalArgumentException exception) {
      player.sendMessage(
        "Usage: /nits <give|remove> <positive whole-number amount>"
      );
      return true;
    }

    UUID playerId = player.getUniqueId();
    CurrencyManager.Purse before = currencyManager.getPurse(
      playerId.toString()
    );
    if (
      operation == CurrencyManager.NitsOperation.REMOVE &&
      amount > before.amount()
    ) {
      player.sendMessage("You only have " + before.amount() + " nits.");
      return true;
    }

    CurrencyManager.Purse after;
    try {
      after = currencyManager.adjustPurse(
        playerId,
        operation,
        amount,
        "admin_command"
      );
    } catch (ArithmeticException exception) {
      player.sendMessage("That would exceed the maximum nits balance.");
      return true;
    }

    String verb = operation == CurrencyManager.NitsOperation.GIVE
      ? "Added"
      : "Removed";
    player.sendMessage(
      verb + " " + amount + " nits. Balance: " + after.amount() + " nits."
    );
    return true;
  }

  @Override
  protected List<String> complete(
    CommandSender sender,
    String alias,
    String[] arguments
  ) {
    if (arguments.length == 1) {
      return OPERATIONS;
    }
    return List.of();
  }

  @Override
  protected CommandArgumentHint argumentHint(
    CommandSender sender,
    String alias,
    String[] arguments
  ) {
    if (arguments.length == 1) {
      return CommandArgumentHint.option(
        "commands.nits.operation",
        "nits operation (give or remove)",
        OPERATIONS
      );
    }
    if (arguments.length == 2) {
      return CommandArgumentHint.integer(
        "commands.nits.amount",
        "whole-number amount of nits"
      );
    }
    return null;
  }
}
