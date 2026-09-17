package dev.bisz.enchants.fishing;

import dev.bisz.items.DevEnchantment;
import dev.bisz.items.DevItemStack;
import dev.bisz.items.EnchantmentId;
import dev.bisz.items.ItemsPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** trueMC's deterministic, automatic fishing loop. */
public final class FishingManager implements Listener {
  private final Plugin plugin;
  private final FishingLootTable lootTable;
  private final Map<UUID, Session> sessions = new HashMap<>();

  public FishingManager(Plugin plugin, Map<String, Object> fishing) {
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
    int delayTicks = Math.max(60, (int) (baseTicks * (1 - reduction)));
    int[] remaining = {-1};
    int[] ticks = {0};
    session.task = new BukkitRunnable() {
      @Override public void run() {
        ticks[0]++;
        if (!session.hook.isValid()) {
          cancel();
          cleanup(player, session);
          return;
        }
        session.indicator.follow(session.hook);
        if (ticks[0] % 20 != 0) return;
        if (session.hook.getLocation().getBlock().getType().isSolid() && ticks[0] > 10) {
          cancel();
          cleanup(player, session);
          return;
        }
        // Fishing only starts once the bobber has settled in water. While it is
        // airborne the countdown is paused and a grounded bobber cancels it.
        if (!hookInWater(session.hook)) {
          if (ticks[0] > 20 && session.hook.getVelocity().lengthSquared() < .01D) {
            cancel();
            cleanup(player, session);
          }
          return;
        }
        if (!session.landed) {
          session.landed = true;
          playHookSound(session.hook, Sound.ENTITY_FISHING_BOBBER_SPLASH, .8F, 1.2F);
          playHookSound(session.hook, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, .7F, .9F);
        }
        if (remaining[0] < 0) {
          // The first second in water starts the displayed countdown at its
          // full value, so a three-second minimum shows 3, 2, 1.
          remaining[0] = delayTicks;
        } else {
          remaining[0] -= 20;
          if (remaining[0] <= 0) {
            cancel();
            session.indicator.remove();
            catchNow(player, session);
            return;
          }
        }
        int seconds = (int) Math.ceil(remaining[0] / 20.0);
        session.indicator.update(session.hook, seconds, session.afk);
        playAmbience(session, seconds);
      }
    };
    session.task.runTaskTimer(plugin, 0L, 1L);
  }

  /** Soft water ambience while waiting, with a rising cue for the last three seconds. */
  private static void playAmbience(Session session, int seconds) {
    if (seconds <= 3) {
      playHookSound(session.hook, Sound.BLOCK_NOTE_BLOCK_HAT, .6F, 1.2F + (3 - seconds) * .15F);
      return;
    }
    if (seconds % 5 == 0) {
      playHookSound(session.hook, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, .25F, .7F + (seconds % 10) * .02F);
    }
  }

  private static boolean hookInWater(FishHook hook) {
    if (hook.isInWater()) return true;
    Location location = hook.getLocation();
    if (location.getBlock().getType() == Material.WATER) return true;
    return location.clone().add(0D, -.1D, 0D).getBlock().getType() == Material.WATER;
  }

  private static void playHookSound(FishHook hook, Sound sound, float volume, float pitch) {
    Location location = hook.getLocation();
    location.getWorld().playSound(location, sound, volume, pitch);
  }

  private void catchNow(Player player, Session session) {
    if (!session.hook.isValid() || !hookInWater(session.hook)) {
      sessions.remove(player.getUniqueId());
      return;
    }
    ItemStack held = player.getInventory().getItemInMainHand();
    boolean silkTouch = level(held, "silk_touch") > 0;
    int fortune = level(held, "fortune");
    ItemStack loot = cast(player, lootTable.roll(silkTouch, fortune));
    reel(player, session, loot);
    player.getWorld().playSound(session.hook.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1F, 1F);
    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1F, 1F);
    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .5F, 1.4F);
    applyChanneling(player, held);

    if (session.afk) {
      session.indicator = FishingIndicator.spawn(session.hook);
      scheduleCatch(player, session);
    } else {
      session.hook.remove();
      sessions.remove(player.getUniqueId());
    }
  }

  /** Runs the catch through ItemLib so overrides render as trueMC items. */
  private static ItemStack cast(Player player, ItemStack loot) {
    try {
      DevItemStack stack = ItemsPlugin.instance().factory().wrap(loot);
      stack.render(player);
      return stack.bukkitStack();
    } catch (RuntimeException ignored) {
      return loot;
    }
  }

  /** Flies the catch from the bobber to the player before delivering it. */
  private void reel(Player player, Session session, ItemStack loot) {
    if (loot == null || loot.getType().isAir()) return;
    Location origin = session.hook.isValid() ? session.hook.getLocation() : player.getLocation();
    Item flight = player.getWorld().dropItem(origin, loot);
    flight.setPickupDelay(32767);
    flight.setGravity(false);
    new BukkitRunnable() {
      private int ticks;
      @Override public void run() {
        if (!player.isOnline()) {
          cancel();
          release(flight);
          return;
        }
        if (!flight.isValid()) {
          cancel();
          return;
        }
        Vector direction = player.getEyeLocation().toVector().subtract(flight.getLocation().toVector());
        if (direction.lengthSquared() <= 1D || ticks++ >= 60) {
          cancel();
          flight.remove();
          player.getInventory().addItem(loot).values().forEach(leftover ->
            player.getWorld().dropItemNaturally(player.getLocation(), leftover));
          return;
        }
        flight.setVelocity(direction.normalize().multiply(.5D));
      }
    }.runTaskTimer(plugin, 1L, 1L);
  }

  private static void release(Item flight) {
    if (!flight.isValid()) return;
    flight.setPickupDelay(0);
    flight.setGravity(true);
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
    private boolean landed;
  }
}
