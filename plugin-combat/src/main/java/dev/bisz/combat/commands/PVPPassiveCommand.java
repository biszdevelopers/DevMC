package dev.bisz.combat.commands;

import dev.bisz.commands.DevCommand;
import dev.bisz.players.locales.LanguageSelectionMenu;
import dev.bisz.players.locales.Locale;
import java.util.List;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PVPPassiveCommand extends DevCommand {

  private final PVPPassiveListener list;

  public PVPPassiveCommand(PVPPassiveListener list) {
    super("pvp", "");
    this.setDescription("Enables / Disables pvp");
    this.setUsage("/pvp");

    this.list = list;
  }

  @Override
  protected boolean executeCommand(
    CommandSender var1,
    String var2,
    String[] var3
  ) {
    if (!(var1 instanceof Player player)) {
      var1.sendMessage("This command can only be used by a player.");
      return true;
    }

    if (this.list.isInPvpStatus(player)) {
      player.sendMessage("§c" + Locale.get(player, "pvp.toggleinfo.blocked"));
      return true;
    }

    if (this.list.hasPVPEnabled(player)) {
      this.list.disablePVP(player);
      player.sendMessage(
        "§e" +
        Locale.get(
          player,
          "pvp.toggleinfo",
          "§l§e" + Locale.get(player, "general.disabled")
        )
      );
      player.playSound(player, Sound.ENTITY_VILLAGER_TRADE, 1f, 1f);
    } else {
      this.list.enablePVP(player);
      player.sendMessage(
        "§a" +
        Locale.get(
          player,
          "pvp.toggleinfo",
          "§l§a" + Locale.get(player, "general.enabled")
        )
      );
      player.playSound(player, Sound.ENTITY_VILLAGER_TRADE, 1f, 1f);
    }

    return true;
  }
}
