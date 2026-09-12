package dev.bisz.combat;

import dev.bisz.commands.DevCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ReviveCommand extends DevCommand {

  private final DeathManager deaths;

  ReviveCommand(DeathManager d) {
    super("revive", "");
    deaths = d;
    setUsage("/revive");
  }

  protected boolean executeCommand(CommandSender s, String l, String[] a) {
    if (!(s instanceof Player p)) {
      s.sendMessage("Players only.");
      return true;
    }
    deaths.openRevive(p);
    return true;
  }
}
