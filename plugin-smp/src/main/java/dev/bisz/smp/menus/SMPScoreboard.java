package dev.bisz.smp.menus;

import dev.bisz.chat.ChatUtils;
import dev.bisz.currency.CurrencyManager;
import dev.bisz.players.locales.Locale;
import dev.bisz.scoreboards.DevScoreboard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class SMPScoreboard extends DevScoreboard {

  private final BukkitTask refreshTask;
  private long purse;
  private boolean inPvp,
    pvpEnabled = true,
    ghost;
  private int remaining;

  SMPScoreboard(JavaPlugin plugin, Player player) {
    super(player, "smp", "§e§lDEVMC");
    show();
    purse = CurrencyManager.getInstance()
      .getPurse(player.getUniqueId().toString())
      .amount();
    renderPage();
    refreshTask = Bukkit.getScheduler().runTaskTimer(
      plugin,
      this::renderPage,
      100,
      100
    );
  }

  public void updatePurse(long value) {
    purse = value;
    renderPage();
  }

  public void updatePvpStatus(boolean active, boolean enabled, int seconds) {
    inPvp = active;
    pvpEnabled = enabled;
    remaining = seconds;
    renderPage();
  }

  public void ghost(boolean value) {
    ghost = value;
    renderPage();
  }

  private void renderPage() {
    if (isDisposed() || !player.isOnline()) return;
    clearLines();
    addLine(signal(player) + "§8" + player.getPing() + "ms");
    if (ghost) {
      addLine(" ");
      addLine("§c" + Locale.get(player, "smp.death.ghost"));
      addLine("§7" + Locale.get(player, "smp.death.revive"));
      addLine("  ");
      addLine("§7mc.bisz.dev");
      return;
    }
    addLine(
      inPvp
        ? "§0§0§0§9§c\uD83D\uDDE1 " + remaining
        : "§0§0§0§9" + (pvpEnabled ? "§7\uD83D\uDDE1" : "§a\uD83D\uDDE1")
    );
    addLine(" ".repeat(15));
    addLine(
      " " +
      Locale.get(player, "currency.nits") +
      " (§e" +
      Locale.get(player, "currency.nits.abbreviation") +
      "§r)"
    );
    addLine("§0§0§0§a  §e" + ChatUtils.commaNumber(purse));
    addLine(" ".repeat(14));
    addLine(" " + Locale.get(player, "land.address"));
    addLine("§0§0§0§b  §7" + Locale.get(player, "land.wilderness"));
    addLine(" ".repeat(13));
    addLine("§7mc.bisz.dev");
  }

  @Override
  protected void onDispose() {
    refreshTask.cancel();
  }

  private static String signal(Player p) {
    int n = p.getPing();
    return (
      (n <= 50
          ? "§a"
          : n <= 100
            ? "§2"
            : n <= 150 ? "§e" : n <= 200 ? "§6" : n <= 250 ? "§c" : "§4") +
      "📶 "
    );
  }
}
