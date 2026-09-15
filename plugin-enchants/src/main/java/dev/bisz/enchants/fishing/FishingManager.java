package dev.bisz.enchants.fishing;

import dev.bisz.items.DevEnchantment;
import dev.bisz.items.DevItemStack;
import dev.bisz.items.EnchantmentId;
import dev.bisz.items.ItemsPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/** trueMC's deterministic, automatic fishing loop. */
public final class FishingManager implements Listener {
  private final Plugin plugin;
  private final FishingLootTable lootTable;
  private final Map<UUID, Session> sessions = new HashMap<>();

  public FishingManager(Plugin plugin, FileConfiguration fishing) {
    this.plugin = plugin;
    this.lootTable = new FishingLootTable();
    this.lootTable.load(fishing);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onFish(PlayerFishEvent event) {
    Player player = event.getPlayer();
    switch (event.getState()) {
      case FISHING -> startCast(player, event.getHook());
      case BITE, FAILED_ATTEMPT, CAUGHT_FISH -> event.setCancelled(true);
      case REEL_IN, IN_GROUND, CAUGHT_ENTITY -> endCast(player);
      default -> {
      }
    }
  }

  private void startCast(Player player, FishHook hook) {
    endCast(player);
    Session session = new Session();
    session.hook = hook;
    session.afk = level(player.getInventory().getItemInMainHand(), "loyalty") > 0;
    session.indicator = FishingIndicator.spawn(hook);
    sessions.put(player.getUniqueId(), session);
    scheduleCatch(player, session);
  }

  private void scheduleCatch(Player player, Session session) {
    int baseTicks = lootTable.baseTime() * 20;
    double reduction = lootTable.efficiencyReduction()
      * level(player.getInventory().getItemInMainHand(), "efficiency");
    int delayTicks = Math.max(20, (int) (baseTicks * (1 - reduction)));
    int[] remaining = {delayTicks};
    int[] elapsed = {0};
    session.task = new BukkitRunnable() {
      @Override public void run() {
        elapsed[0] += 20;
        if (!session.hook.isValid()) {
          cancel();
          cleanup(player, session);
          return;
        }
        if (session.hook.getLocation().getBlock().getType().isSolid() && elapsed[0] > 10) {
          cancel();
          cleanup(player, session);
          return;
        }
        remaining[0] -= 20;
        int seconds = Math.max(1, (int) Math.ceil(remaining[0] / 20.0));
        session.indicator.update(session.hook, seconds, session.afk);
        if (remaining[0] <= 0) {
          cancel();
          session.indicator.remove();
          catchNow(player, session);
        }
      }
    };
    session.task.runTaskTimer(plugin, 0L, 20L);
  }

  private void catchNow(Player player, Session session) {
    if (!session.hook.isValid()) {
      sessions.remove(player.getUniqueId());
      return;
    }
    ItemStack held = player.getInventory().getItemInMainHand();
    boolean silkTouch = level(held, "silk_touch") > 0;
    int fortune = level(held, "fortune");
    ItemStack loot = lootTable.roll(silkTouch, fortune);
    if (!player.getInventory().addItem(loot).isEmpty()) {
      player.getWorld().dropItem(player.getLocation(), loot);
    }
    player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1F, 1F);
    applyChanneling(player, held);

    if (session.afk) {
      session.indicator = FishingIndicator.spawn(session.hook);
      scheduleCatch(player, session);
    } else {
      session.hook.remove();
      sessions.remove(player.getUniqueId());
    }
  }

  private void endCast(Player player) {
    Session session = sessions.remove(player.getUniqueId());
    if (session != null) cleanup(player, session);
  }

  private void cleanup(Player player, Session session) {
    if (session.task != null) session.task.cancel();
    if (session.indicator != null) session.indicator.remove();
    sessions.remove(player.getUniqueId());
  }

  private void applyChanneling(Player player, ItemStack held) {
    int channeling = level(held, "channeling");
    if (channeling <= 0) return;
    World world = player.getWorld();
    world.setStorm(true);
    world.setWeatherDuration(world.getWeatherDuration() + 200 * channeling);
    if (world.getWeatherDuration() > 12000) {
      world.setThundering(true);
      world.setThunderDuration(200 * channeling);
    }
  }

  private static int level(ItemStack item, String path) {
    if (item == null || item.getType().isAir()) return 0;
    try {
      DevItemStack stack = ItemsPlugin.instance().factory().wrap(item);
      DevEnchantment enchantment = ItemsPlugin.instance().enchantments()
        .get(EnchantmentId.of("enchants", path)).orElse(null);
      if (enchantment == null) enchantment = ItemsPlugin.instance().enchantments()
        .get(EnchantmentId.of("minecraft", path)).orElse(null);
      return enchantment == null ? 0 : stack.enchantmentLevel(enchantment).orElse(0);
    } catch (RuntimeException ignored) {
      return 0;
    }
  }

  private static final class Session {
    private FishHook hook;
    private FishingIndicator indicator;
    private BukkitRunnable task;
    private boolean afk;
  }
}
