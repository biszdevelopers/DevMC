/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.jetbrains.annotations.NotNull
 */
package dev.bisz.commands;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.players.Rank;
import dev.bisz.players.locales.Locale;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public abstract class DevCommand extends Command {

  private final String permission;
  private Rank minimumRank = Rank.NONE;
  private boolean operatorRequired;

  protected DevCommand(String name, String permission) {
    super(Objects.requireNonNull(name, "name"));
    this.permission = Objects.requireNonNull(permission, "permission");
  }

  public final boolean execute(
    @NotNull CommandSender sender,
    @NotNull String label,
    @NotNull String[] arguments
  ) {
    if (!this.isAuthorized(sender, true)) {
      return true;
    }
    try {
      return this.executeCommand(sender, label, arguments);
    } catch (RuntimeException exception) {
      sender.sendMessage(this.executionFailureMessage(sender));
      throw exception;
    }
  }

  protected String permissionDeniedMessage(CommandSender sender) {
    return sender instanceof Player player
      ? "§c" + Locale.get(player, "commands.no_permission")
      : "Permission denied.";
  }

  protected String executionFailureMessage(CommandSender sender) {
    return sender instanceof Player player
      ? "§c" + Locale.get(player, "commands.error")
      : "Command execution failed.";
  }

  protected abstract boolean executeCommand(
    CommandSender var1,
    String var2,
    String[] var3
  );

  public List<String> tabComplete(
    @NotNull CommandSender sender,
    @NotNull String alias,
    @NotNull String[] arguments
  ) {
    if (!this.isAuthorized(sender, false)) {
      return List.of();
    }
    CommandArgumentHint hint = this.argumentHint(sender, alias, arguments);
    LinkedHashSet<String> candidates = new LinkedHashSet<>(
      this.complete(sender, alias, arguments)
    );
    if (hint != null) {
      candidates.addAll(hint.suggestions());
    }
    String typed = arguments.length == 0 ? "" : arguments[arguments.length - 1];
    List<String> matches = candidates
      .stream()
      .filter(candidate ->
        candidate.regionMatches(true, 0, typed, 0, typed.length())
      )
      .toList();
    if (hint == null) {
      return matches;
    }
    List<String> result = new ArrayList<>();
    result.add(" " + this.renderHint(sender, hint));
    if (hint.type().supportsMathExpressions()) {
      result.add(" " + this.renderMathHint(sender, hint.type(), typed));
    }
    result.addAll(matches);
    return List.copyOf(result);
  }

  protected List<String> complete(
    CommandSender sender,
    String alias,
    String[] arguments
  ) {
    return List.of();
  }

  protected CommandArgumentHint argumentHint(
    CommandSender sender,
    String alias,
    String[] arguments
  ) {
    return null;
  }

  protected final void requireRank(Rank rank) {
    this.minimumRank = Objects.requireNonNull(rank, "rank");
  }

  protected final void requireOperator() {
    this.operatorRequired = true;
  }

  private boolean isAuthorized(CommandSender sender, boolean reportFailure) {
    if (!this.permission.isEmpty() && !sender.hasPermission(this.permission)) {
      if (reportFailure) {
        sender.sendMessage(this.permissionDeniedMessage(sender));
      }
      return false;
    }
    if (!(sender instanceof org.bukkit.entity.Player player)) {
      return true;
    }
    if (
      (this.operatorRequired && !player.isOp()) ||
      BundlerPlugin.instance().rankService().rank(player).level() <
      this.minimumRank.level()
    ) {
      if (reportFailure) {
        sender.sendMessage(this.permissionDeniedMessage(sender));
      }
      return false;
    }
    return true;
  }

  private String renderHint(CommandSender sender, CommandArgumentHint hint) {
    if (sender instanceof org.bukkit.entity.Player player) {
      String type = Locale.get(player, hint.type().localeKey());
      String description = Locale.get(player, hint.descriptionKey());
      String rendered = Locale.get(
        player,
        "commands.general.tabcomplete.argument_hint",
        type,
        description
      );
      return hint.optional()
        ? Locale.get(player, "commands.general.tabcomplete.optional", rendered)
        : rendered;
    }
    String rendered = "[%s: %s]".formatted(
      hint.type().englishName(),
      hint.englishDescription()
    );
    return hint.optional() ? "(optional) " + rendered : rendered;
  }

  private String renderMathHint(
    CommandSender sender,
    CommandArgumentType type,
    String input
  ) {
    if (input.isEmpty()) {
      return this.localizedMessage(
        sender,
        "commands.general.tabcomplete.math_supported",
        "Mathematical operations are supported."
      );
    }

    try {
      double value = MathExpressions.evaluateNumber(input);
      return this.localizedMessage(
        sender,
        "commands.general.tabcomplete.math_result",
        "Result: %s = %s",
        input,
        this.renderMathValue(type, value)
      );
    } catch (IllegalArgumentException exception) {
      return this.localizedMessage(
        sender,
        "commands.general.tabcomplete.math_error",
        "Invalid Expression"
      );
    }
  }

  private String renderMathValue(CommandArgumentType type, double value) {
    if (type != CommandArgumentType.INTEGER || value != Math.rint(value)) {
      String rendered = Double.toString(value);
      if (type == CommandArgumentType.INTEGER) {
        rendered += " ≈ " + Math.round(value);
      }
      return rendered;
    }
    return Long.toString((long) value);
  }

  private String localizedMessage(
    CommandSender sender,
    String key,
    String fallback,
    Object... arguments
  ) {
    if (sender instanceof org.bukkit.entity.Player player) {
      return Locale.get(player, key, arguments);
    }
    return fallback.formatted(arguments);
  }
}
