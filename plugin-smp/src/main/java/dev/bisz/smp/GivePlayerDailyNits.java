package dev.bisz.smp;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.currency.CurrencyManager;
import dev.bisz.players.locales.Locale;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class GivePlayerDailyNits {

  public static void start() {
    Bukkit.getScheduler().scheduleSyncRepeatingTask(
        BundlerPlugin.instance(),
        new Runnable() {
          public void run() {
            // Code to give daily nits to players

            for (Player player : Bukkit.getOnlinePlayers()) {
              give(player);
            }
          }
        },
        0L,
        20L * 60 * 20
      );
  }

  public static void give(Player player) {
    int nits = new Random().nextInt(500) + 500; // Random number between 0 and 999

    CurrencyManager.getInstance().adjustPurse(
      player.getUniqueId(),
      CurrencyManager.NitsOperation.GIVE,
      nits,
      "daily_reward"
    );

    String b = Locale.get(
      player,
      "daily.nits.message",
      String.format("%,d", nits)
    );

    player.sendMessage(b);

    player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0f);
  }
}
