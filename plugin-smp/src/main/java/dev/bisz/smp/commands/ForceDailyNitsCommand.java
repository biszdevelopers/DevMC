package dev.bisz.smp.commands;

import dev.bisz.commands.DevCommand;
import dev.bisz.players.Rank;
import dev.bisz.players.locales.Locale;
import dev.bisz.smp.GivePlayerDailyNits;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ForceDailyNitsCommand extends DevCommand {

  public ForceDailyNitsCommand() {
    super("forcedailynits", "smp.debug");
    setUsage("/forcedailynits [player]");
    requireRank(Rank.ADMIN);
    requireOperator();
  }

  @Override
  protected boolean executeCommand(
    CommandSender sender,
    String label,
    String[] arguments
  ) {
    if (arguments.length > 1) {
      sender.sendMessage(
        message(
          sender,
          "command.forcedailynits.usage",
          "§cUsage: /forcedailynits [player]"
        )
      );
      return true;
    }

    Player target = arguments.length == 1
      ? Bukkit.getPlayerExact(arguments[0])
      : sender instanceof Player player ? player : null;
    if (target == null) {
      sender.sendMessage(
        message(
          sender,
          "command.forcedailynits.player_not_found",
          "§cPlayer not found. Console senders must specify an online player."
        )
      );
      return true;
    }

    GivePlayerDailyNits.give(target);
    sender.sendMessage(
      message(
        sender,
        "command.forcedailynits.success",
        "§aForced the daily nits reward for %s.",
        target.getName()
      )
    );
    return true;
  }

  @Override
  protected List<String> complete(
    CommandSender sender,
    String alias,
    String[] arguments
  ) {
    if (arguments.length != 1) return List.of();
    return Bukkit.getOnlinePlayers()
      .stream()
      .map(Player::getName)
      .filter(name ->
        name.regionMatches(true, 0, arguments[0], 0, arguments[0].length())
      )
      .sorted(String.CASE_INSENSITIVE_ORDER)
      .toList();
  }

  @Override
  protected String permissionDeniedMessage(CommandSender sender) {
    return message(
      sender,
      "command.forcedailynits.permission_denied",
      "§cYou do not have permission to use this debug command."
    );
  }

  @Override
  protected String executionFailureMessage(CommandSender sender) {
    return message(
      sender,
      "command.forcedailynits.failure",
      "§cThe daily-nits debug command failed. Check the server log."
    );
  }

  private String message(
    CommandSender sender,
    String key,
    String fallback,
    Object... arguments
  ) {
    if (!(sender instanceof Player player)) return fallback.formatted(
      arguments
    );
    String translated = Locale.get(player, key, arguments);
    return translated.equals(key) ? fallback.formatted(arguments) : translated;
  }
}
