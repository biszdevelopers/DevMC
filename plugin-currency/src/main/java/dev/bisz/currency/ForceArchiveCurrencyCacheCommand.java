package dev.bisz.currency;

import dev.bisz.commands.DevCommand;
import dev.bisz.players.Rank;
import org.bukkit.command.CommandSender;

final class ForceArchiveCurrencyCacheCommand extends DevCommand {

  private final CurrencyPlugin plugin;

  ForceArchiveCurrencyCacheCommand(CurrencyPlugin plugin) {
    super("forcearchivecurrencycache", "currency.archive.debug");
    this.plugin = plugin;
    setUsage("/forcearchivecurrencycache");
    requireRank(Rank.ADMIN);
    requireOperator();
  }

  @Override
  protected boolean executeCommand(
    CommandSender sender,
    String label,
    String[] arguments
  ) {
    if (arguments.length != 0) {
      sender.sendMessage("§cUsage: /forcearchivecurrencycache");
      return true;
    }
    CurrencyManager.TransactionArchive archive =
      plugin.forceArchiveCurrencyCache();
    sender.sendMessage(
      "§aArchived " +
      archive.transactionCount() +
      " currency transaction(s) to " +
      archive.file() +
      ". A new cache session is now active."
    );
    return true;
  }

  @Override
  protected String permissionDeniedMessage(CommandSender sender) {
    return "§cYou do not have permission to archive the currency cache.";
  }

  @Override
  protected String executionFailureMessage(CommandSender sender) {
    return "§cThe currency cache could not be archived. Its transactions were retained.";
  }
}
