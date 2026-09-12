package dev.bisz.combat;

import dev.bisz.commands.DevCommand;
import dev.bisz.menus.StorageAccess;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class DeathStashCommand extends DevCommand {

  private final DeathManager deaths;

  DeathStashCommand(DeathManager d) {
    super("deathstash", "");
    deaths = d;
    setUsage("/deathstash");
  }

  protected boolean executeCommand(CommandSender s, String l, String[] a) {
    if (!(s instanceof Player p)) {
      s.sendMessage("Players only.");
      return true;
    }
    if (deaths.isGhost(p)) {
      p.sendMessage("§cGhosts cannot access a stash.");
      return true;
    }
    deaths.openStash(p, p.getUniqueId(), StorageAccess.TAKE_ONLY);
    return true;
  }
}
