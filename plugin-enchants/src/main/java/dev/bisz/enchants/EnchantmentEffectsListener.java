package dev.bisz.enchants;

import dev.bisz.items.DevEnchantment;
import dev.bisz.items.DevItemStack;
import dev.bisz.items.Ability;
import dev.bisz.items.AbilityCooldown;
import dev.bisz.items.EnchantmentId;
import dev.bisz.items.ItemsPlugin;
import dev.bisz.items.ItemHandEquipEvent;
import dev.bisz.items.ItemStackSerializer;
import dev.bisz.items.RomanNumerals;
import dev.bisz.players.locales.Locale;
import dev.bisz.enchants.items.InflameEnchantment;
import dev.bisz.enchants.items.NimbleEnchantment;
import dev.bisz.enchants.items.WingedEnchantment;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.block.Action;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/** Runtime mechanics that require events beyond ItemLib's damage hooks. */
final class EnchantmentEffectsListener implements Listener {
  private static final UUID NIMBLE_MODIFIER = UUID.fromString("78b835f2-9cf0-47bb-b17d-7293e3844072");
  private final EnchantsPlugin plugin;
  private final NamespacedKey projectileInflame;
  private final NamespacedKey legacyShortbowAmmo;
  private final Set<UUID> wingedFallProtection = new HashSet<>();
  private final Map<UUID, AbilityCooldown> wingedCooldowns = new HashMap<>();
  private final Map<UUID, AbilityCooldown> shortbowCooldowns = new HashMap<>();

  EnchantmentEffectsListener(EnchantsPlugin plugin) {
    this.plugin = plugin;
    projectileInflame = new NamespacedKey(plugin, "projectile_inflame");
    legacyShortbowAmmo = new NamespacedKey(plugin, "shortbow_ammo");
    plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickPlayers, 1L, 1L);
    plugin.getServer().getScheduler().runTask(plugin,
      () -> plugin.getServer().getOnlinePlayers().forEach(this::restoreLegacyShortbowAmmo));
  }

  void dispose() { plugin.getServer().getOnlinePlayers().forEach(player -> {
    restoreLegacyShortbowAmmo(player);
    cleanup(player);
  }); }

  @EventHandler(ignoreCancelled = true)
  void projectileLaunch(ProjectileLaunchEvent event) {
    Projectile projectile = event.getEntity();
    if (!(projectile.getShooter() instanceof Player player)) return;
    ItemStack weapon = player.getInventory().getItemInMainHand();
    int penetration = level(weapon, "penetration");
    int multishot = level(weapon, "multishot");
    int pierce = Math.max(penetration, multishot);
    if (pierce > 0 && projectile instanceof AbstractArrow arrow) arrow.setPierceLevel(Math.min(127, pierce));
    int nimble = level(weapon, "nimble");
    if (nimble > 0 && projectile.getType() == org.bukkit.entity.EntityType.TRIDENT)
      projectile.setVelocity(projectile.getVelocity().multiply(1D + .1D * nimble));
  }

  @EventHandler(ignoreCancelled = true)
  void configureBowProjectile(EntityShootBowEvent event) {
    if (event.getEntity() instanceof Player player && event.getProjectile() instanceof AbstractArrow arrow && event.getBow() != null)
      configureBowArrow(arrow, player, event.getBow());
  }

  /**
   * Projectile enchantments are stamped onto the arrow when it is fired. This
   * keeps the effect reliable after the weapon is swapped and for every
   * separately spawned Multishot projectile.
   */
  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  void inflameProjectile(EntityDamageByEntityEvent event) {
    if (!(event.getDamager() instanceof AbstractArrow arrow)
      || !(event.getEntity() instanceof org.bukkit.entity.LivingEntity target)) return;
    Integer inflame = arrow.getPersistentDataContainer()
      .get(projectileInflame, PersistentDataType.INTEGER);
    if (inflame == null || inflame <= 0) return;
    target.setFireTicks(InflameEnchantment.resultingFireTicks(target.getFireTicks(), inflame, true));
  }

  // Bukkit pre-cancels LEFT_CLICK_AIR for bows because vanilla has no action
  // for it. Shortbow deliberately gives that otherwise inert click a meaning.
  @EventHandler
  void shortbow(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND
      || (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK)) return;
    ItemStack bow = event.getItem();
    Player player = event.getPlayer();
    if (bow == null || bow.getType() != Material.BOW || level(bow, "nimble") == 0) return;
    if (fireShortbow(player, bow)) event.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  void shortbowMelee(EntityDamageByEntityEvent event) {
    if (!(event.getDamager() instanceof Player player)) return;
    ItemStack bow = player.getInventory().getItemInMainHand();
    if (!isNimbleBow(bow)) return;
    if (fireShortbow(player, bow)) event.setCancelled(true);
  }

  private boolean fireShortbow(Player player, ItemStack bow) {
    long now = System.currentTimeMillis();
    if (cooldownActive(shortbowCooldowns, player, now)) return false;
    ShortbowAmmo ammo = findShortbowAmmo(player);
    if (ammo == null) return false;
    AbstractArrow arrow = launchShortbowArrow(player, ammo.original());
    arrow.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(3D));
    arrow.setCritical(true);
    configureBowArrow(arrow, player, bow);
    if (player.getGameMode() != GameMode.CREATIVE) consumeShortbowAmmo(player, ammo);
    damageItem(player, EquipmentSlot.HAND, bow, ThreadLocalRandom.current());
    shortbowCooldowns.put(player.getUniqueId(), AbilityCooldown.start(NimbleEnchantment.SHORTBOW, now));
    player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1F, 1.2F);
    return true;
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  void grapple(PlayerFishEvent event) {
    if (event.getState() != PlayerFishEvent.State.REEL_IN
      && event.getState() != PlayerFishEvent.State.IN_GROUND
      && event.getState() != PlayerFishEvent.State.FAILED_ATTEMPT) return;
    Player player = event.getPlayer();
    ItemStack rod = riptideFishingRod(player, event.getHand());
    int riptide = level(rod, "riptide");
    if (riptide <= 0 || !grappleEnvironment(player)) return;
    Vector displacement = event.getHook().getLocation().toVector()
      .subtract(player.getEyeLocation().toVector());
    if (displacement.lengthSquared() < .25D) return;
    player.setVelocity(RiptideFishingRodSpecialty.pullVelocity(displacement, riptide));
    player.setFallDistance(0F);
    damageItem(player, event.getHand(), rod, ThreadLocalRandom.current());
  }

  /** Applies normal one-point item use damage, including Unbreaking and Bukkit damage events. */
  private void damageItem(
    Player player, EquipmentSlot hand, ItemStack item, RandomGenerator random
  ) {
    if (player.getGameMode() == GameMode.CREATIVE) return;
    int unbreaking = Math.max(0, item.getEnchantmentLevel(Enchantment.DURABILITY));
    if (!consumesDurability(unbreaking, random)) return;
    PlayerItemDamageEvent damageEvent = new PlayerItemDamageEvent(player, item, 1);
    plugin.getServer().getPluginManager().callEvent(damageEvent);
    if (damageEvent.isCancelled() || damageEvent.getDamage() <= 0) return;
    ItemMeta itemMeta = item.getItemMeta();
    if (!(itemMeta instanceof Damageable damageable)) return;
    int nextDamage = damageable.getDamage() + damageEvent.getDamage();
    if (nextDamage >= item.getType().getMaxDurability()) {
      ItemStack broken = item.clone();
      player.getInventory().setItem(hand, null);
      player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1F, 1F);
      plugin.getServer().getPluginManager().callEvent(new PlayerItemBreakEvent(player, broken));
      return;
    }
    damageable.setDamage(nextDamage);
    item.setItemMeta(itemMeta);
    player.getInventory().setItem(hand, item);
  }

  /** Tracks every repaired point and scales vanilla Mending by its level multiplier. */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  void limitMending(PlayerItemMendEvent event) {
    DevItemStack stack;
    try { stack = ItemsPlugin.instance().factory().wrap(event.getItem()); }
    catch (RuntimeException ignored) { return; }
    if (!(stack.definition() instanceof SocketedVanillaItem)) return;
    Map.Entry<DevEnchantment, dev.bisz.items.EnchantmentData> mending = stack.enchantmentData().entrySet().stream()
      .filter(entry -> SocketedVanillaItem.isMending(entry.getKey())).findFirst().orElse(null);
    if (mending == null) return;
    int count = SocketedVanillaItem.mendCount(mending.getValue());
    int remainder = SocketedVanillaItem.mendRemainder(mending.getValue());
    SocketedVanillaItem.MendingRepair repair = SocketedVanillaItem.scaleMendingRepair(
      event.getRepairAmount(), missingDurability(event.getItem()), count, remainder);
    event.setRepairAmount(repair.repaired());
    if (repair.remainder() != remainder)
      stack.setEnchantmentMetadata(mending.getKey(), SocketedVanillaItem.mendingRemainderKey(), repair.remainder());
    if (repair.repaired() <= 0) return;
    int after = count + repair.repaired();
    int level = SocketedVanillaItem.mendingLevel(after);
    if (level != mending.getValue().level()) stack.enchant(mending.getKey(), level);
    stack.setEnchantmentMetadata(mending.getKey(), SocketedVanillaItem.mendingCountKey(), after);
    if (level > mending.getValue().level()) announceMendingUpgrade(event.getPlayer(), level);
  }

  private static void announceMendingUpgrade(Player player, int level) {
    player.sendMessage(Locale.get(player, "enchants.mending.upgrade",
      RomanNumerals.format(level), SocketedVanillaItem.multiplierText(level)));
    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .8F, 1.2F);
  }

  private static int missingDurability(ItemStack item) {
    return item.getItemMeta() instanceof Damageable damageable ? Math.max(0, damageable.getDamage()) : 0;
  }

  private static boolean grappleEnvironment(Player player) {
    if (player.isInWater()) return true;
    var location = player.getLocation();
    var block = location.getBlock();
    boolean exposed = location.getBlockY() > location.getWorld()
      .getHighestBlockYAt(location.getBlockX(), location.getBlockZ());
    return grappleEnvironment(false, location.getWorld().hasStorm(),
      block.getTemperature(), block.getHumidity(), exposed);
  }

  static boolean grappleEnvironment(
    boolean inWater, boolean storm, double temperature, double humidity, boolean exposed
  ) {
    return inWater || (storm && exposed && humidity > 0D && temperature >= .15D);
  }

  static boolean consumesDurability(int unbreakingLevel, RandomGenerator random) {
    return random.nextInt(Math.max(0, unbreakingLevel) + 1) == 0;
  }

  @EventHandler(ignoreCancelled = true)
  void shortbowEquipped(ItemHandEquipEvent event) {
    if (!isNimbleBow(event.item().bukkitStack())) return;
    if (event.hand() == EquipmentSlot.OFF_HAND) {
      plugin.getServer().getScheduler().runTask(plugin, () -> removeShortbowFromOffhand(event.player()));
    }
  }

  @EventHandler
  void shortbowJoin(PlayerJoinEvent event) {
    plugin.getServer().getScheduler().runTask(plugin, () -> restoreLegacyShortbowAmmo(event.getPlayer()));
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  void preventShortbowOffhand(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) return;
    boolean directOffhandPlacement = event.getClickedInventory() instanceof PlayerInventory
      && event.getSlot() == 40 && isNimbleBow(event.getCursor());
    boolean swapIntoOffhand = event.getClick() == ClickType.SWAP_OFFHAND
      && isNimbleBow(event.getCurrentItem());
    if (directOffhandPlacement || swapIntoOffhand) event.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  void preventShortbowOffhand(InventoryDragEvent event) {
    if (!isNimbleBow(event.getOldCursor())) return;
    int topSize = event.getView().getTopInventory().getSize();
    if (event.getRawSlots().stream().anyMatch(raw -> raw >= topSize && event.getView().convertSlot(raw) == 40))
      event.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  void preventShortbowOffhand(PlayerSwapHandItemsEvent event) {
    if (isNimbleBow(event.getOffHandItem())) event.setCancelled(true);
  }

  /**
   * trueMC's Winged activation: while winged boots are worn, flight is granted
   * whenever the player is on the ground, so a single jump-press consumes it.
   */
  @SuppressWarnings("deprecation")
  @EventHandler
  void wingedMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    if (!survival(player)) return;
    if (level(player.getInventory().getBoots(), "winged") > 0) {
      if (player.isOnGround() && !cooldownActive(wingedCooldowns, player, System.currentTimeMillis()))
        player.setAllowFlight(true);
    } else if (player.getAllowFlight() && !player.isFlying()) {
      player.setAllowFlight(false);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  void wingedToggle(PlayerToggleFlightEvent event) {
    Player player = event.getPlayer();
    if (!survival(player)) return;
    if (level(player.getInventory().getBoots(), "winged") <= 0) return;
    if (!event.isFlying()) return;
    event.setCancelled(true);
    player.setFlying(false);
    player.setAllowFlight(false);
    if (cooldownActive(wingedCooldowns, player, System.currentTimeMillis())) return;
    long now = System.currentTimeMillis();
    wingedCooldowns.put(player.getUniqueId(), AbilityCooldown.start(WingedEnchantment.DOUBLE_JUMP, now));
    wingedFallProtection.add(player.getUniqueId());
    player.setFallDistance(0F);
    player.setVelocity(wingedLaunchVelocity(player.getVelocity(), player.getLocation().getDirection()));
  }

  /** Cancels fall damage during the double jump and its three-second cooldown. */
  @EventHandler(ignoreCancelled = true)
  void preventWingedFallDamage(EntityDamageEvent event) {
    if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) return;
    if (level(player.getInventory().getBoots(), "winged") == 0) return;
    if (wingedProtectionActive(wingedCooldowns.get(player.getUniqueId()),
      wingedFallProtection.contains(player.getUniqueId()), System.currentTimeMillis())) event.setCancelled(true);
  }

  /** Channeling boosts the lightning damage of nearby trident/rod holders. */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  void channeling(EntityDamageEvent event) {
    if (event.getCause() != EntityDamageEvent.DamageCause.LIGHTNING) return;
    int best = 0;
    for (org.bukkit.entity.Entity nearby : event.getEntity().getWorld()
      .getNearbyEntities(event.getEntity().getLocation(), 16, 16, 16)) {
      if (!(nearby instanceof Player player)) continue;
      int level = Math.max(level(player.getInventory().getItemInMainHand(), "channeling"),
        level(player.getInventory().getItemInOffHand(), "channeling"));
      if (level > best) best = level;
    }
    if (best > 0) event.setDamage(event.getDamage() + 3D * best);
  }

  /** Floats a deduplicated damage number above player-attributed hits. */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void damageIndicator(EntityDamageByEntityEvent event) {
    if (!DamageIndicator.isEnabled()) return;
    boolean playerSource = event.getDamager() instanceof Player
      || (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player);
    if (playerSource) DamageIndicator.show(plugin, event.getEntity(), event.getFinalDamage());
  }

  @EventHandler void quit(PlayerQuitEvent event) {
    restoreLegacyShortbowAmmo(event.getPlayer());
    cleanup(event.getPlayer());
  }

  @SuppressWarnings("deprecation")
  private void tickPlayers() {
    for (Player player : plugin.getServer().getOnlinePlayers()) {
      long now = System.currentTimeMillis();
      updateAbilityActionBar(player, now);
      updateNimble(player);
      UUID id = player.getUniqueId();
      if (!survival(player)) {
        wingedFallProtection.remove(id);
        wingedCooldowns.remove(id);
        shortbowCooldowns.remove(id);
        removeNimble(player);
        continue;
      }
      if (level(player.getInventory().getBoots(), "winged") == 0) {
        wingedFallProtection.remove(id);
        wingedCooldowns.remove(id);
        continue;
      }
      if (player.isOnGround()) wingedFallProtection.remove(id);
    }
  }

  private void updateAbilityActionBar(Player player, long now) {
    List<String> entries = new ArrayList<>(2);
    appendCooldownActionBar(entries, wingedCooldowns, player, now);
    appendCooldownActionBar(entries, shortbowCooldowns, player, now);
    if (!entries.isEmpty()) sendActionBar(player, Ability.joinActionBars(entries));
  }

  private static void appendCooldownActionBar(
    List<String> entries, Map<UUID, AbilityCooldown> cooldowns, Player player, long now
  ) {
    AbilityCooldown cooldown = cooldowns.get(player.getUniqueId());
    if (cooldown == null) return;
    entries.add(cooldown.actionBar(player, now));
    if (!cooldown.active(now)) cooldowns.remove(player.getUniqueId());
  }

  private static void sendActionBar(Player player, String text) {
    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
  }

  static boolean wingedProtectionActive(AbilityCooldown cooldown, long now) {
    return cooldown != null && cooldown.active(now);
  }

  static boolean wingedProtectionActive(AbilityCooldown cooldown, boolean sinceDoubleJump, long now) {
    return sinceDoubleJump || wingedProtectionActive(cooldown, now);
  }

  static Vector wingedLaunchVelocity(Vector current, Vector facing) {
    Vector direction = facing.clone().setY(0.5D);
    if (direction.lengthSquared() > 0D) direction.normalize();
    return direction.multiply(0.9D);
  }

  private static boolean cooldownActive(
    Map<UUID, AbilityCooldown> cooldowns, Player player, long now
  ) {
    AbilityCooldown cooldown = cooldowns.get(player.getUniqueId());
    if (cooldown == null) return false;
    if (cooldown.active(now)) return true;
    cooldowns.remove(player.getUniqueId());
    return false;
  }

  private void configureBowArrow(AbstractArrow arrow, Player player, ItemStack weapon) {
    arrow.setShooter(player);
    arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    int penetration = level(weapon, "penetration");
    if (penetration > 0) arrow.setPierceLevel(Math.min(127, penetration));
    int inflame = level(weapon, "inflame");
    if (inflame > 0) {
      arrow.getPersistentDataContainer().set(projectileInflame, PersistentDataType.INTEGER, inflame);
      arrow.setFireTicks(100);
    }
    ItemsPlugin.instance().captureProjectileSource(arrow, weapon);
    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
      if (arrow.isValid()) arrow.remove();
    }, 100L);
  }

  private static AbstractArrow launchShortbowArrow(Player player, ItemStack ammunition) {
    if (ammunition.getType() == Material.SPECTRAL_ARROW)
      return player.launchProjectile(SpectralArrow.class);
    Arrow arrow = player.launchProjectile(Arrow.class);
    if (ammunition.getType() == Material.TIPPED_ARROW && ammunition.getItemMeta() instanceof PotionMeta potion)
      applyTippedArrowEffects(arrow, potion);
    return arrow;
  }

  /** Copies both vanilla and custom potion payloads from tipped ammunition. */
  static void applyTippedArrowEffects(Arrow arrow, PotionMeta potion) {
    arrow.setBasePotionData(potion.getBasePotionData());
    potion.getCustomEffects().forEach(effect -> arrow.addCustomEffect(effect, true));
    if (potion.hasColor()) arrow.setColor(potion.getColor());
  }

  /** Restores ammunition produced by releases that used the removed dummy-ammo system. */
  private void restoreLegacyShortbowAmmo(Player player) {
    for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
      ItemStack dummy = player.getInventory().getItem(slot);
      ItemStack restored = restoredLegacyArrow(dummy);
      if (restored == null) continue;
      restored.setAmount(dummy.getAmount());
      player.getInventory().setItem(slot, restored);
    }
  }

  private ShortbowAmmo findShortbowAmmo(Player player) {
    for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
      ItemStack item = player.getInventory().getItem(slot);
      if (!isArrow(item)) continue;
      ItemStack projectile = item.clone();
      projectile.setAmount(1);
      return new ShortbowAmmo(slot, projectile);
    }
    return null;
  }

  private void consumeShortbowAmmo(Player player, ShortbowAmmo ammo) {
    ItemStack arrows = player.getInventory().getItem(ammo.slot());
    if (!isArrow(arrows)) return;
    if (arrows.getAmount() > 1) arrows.setAmount(arrows.getAmount() - 1);
    else player.getInventory().setItem(ammo.slot(), null);
  }

  private ItemStack restoredLegacyArrow(ItemStack dummy) {
    if (!isLegacyShortbowAmmo(dummy)) return null;
    String encoded = dummy.getItemMeta().getPersistentDataContainer()
      .get(legacyShortbowAmmo, PersistentDataType.STRING);
    if (encoded == null) return null;
    try { return ItemStackSerializer.deserializePayload(encoded); }
    catch (RuntimeException exception) { return null; }
  }

  private boolean isLegacyShortbowAmmo(ItemStack item) {
    return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
      .has(legacyShortbowAmmo, PersistentDataType.STRING);
  }

  private void removeShortbowFromOffhand(Player player) {
    ItemStack offhand = player.getInventory().getItemInOffHand();
    if (!isNimbleBow(offhand)) return;
    player.getInventory().setItemInOffHand(null);
    player.getInventory().addItem(offhand).values().forEach(leftover ->
      player.getWorld().dropItemNaturally(player.getLocation(), leftover));
  }

  private boolean isNimbleBow(ItemStack item) {
    return item != null && item.getType() == Material.BOW && level(item, "nimble") > 0;
  }

  private ItemStack riptideFishingRod(Player player, EquipmentSlot hand) {
    ItemStack used = hand == EquipmentSlot.OFF_HAND
      ? player.getInventory().getItemInOffHand()
      : player.getInventory().getItemInMainHand();
    return used.getType() == Material.FISHING_ROD && level(used, "riptide") > 0 ? used : null;
  }

  static boolean isArrow(ItemStack item) {
    return item != null && (item.getType() == Material.ARROW || item.getType() == Material.SPECTRAL_ARROW
      || item.getType() == Material.TIPPED_ARROW);
  }

  private record ShortbowAmmo(int slot, ItemStack original) {}

  @SuppressWarnings("deprecation")
  private void updateNimble(Player player) {
    AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
    if (attribute == null) return;
    int level = level(player.getInventory().getItemInMainHand(), "nimble");
    AttributeModifier current = attribute.getModifiers().stream()
      .filter(modifier -> modifier.getUniqueId().equals(NIMBLE_MODIFIER)).findFirst().orElse(null);
    double amount = level * .25D;
    if (current != null && level > 0 && Double.compare(current.getAmount(), amount) == 0) return;
    if (current != null) attribute.removeModifier(current);
    if (level > 0) attribute.addModifier(new AttributeModifier(NIMBLE_MODIFIER, "enchants-nimble", amount, AttributeModifier.Operation.ADD_NUMBER));
  }

  private int level(ItemStack item, String path) {
    if (item == null || item.getType().isAir()) return 0;
    try {
      DevItemStack stack = ItemsPlugin.instance().factory().wrap(item);
      DevEnchantment enchantment = ItemsPlugin.instance().enchantments()
        .get(EnchantmentId.of("enchants", path)).orElse(null);
      if (enchantment == null) enchantment = ItemsPlugin.instance().enchantments()
        .get(EnchantmentId.of("minecraft", path)).orElse(null);
      return enchantment == null ? 0 : stack.enchantmentLevel(enchantment).orElse(0);
    } catch (RuntimeException ignored) { return 0; }
  }

  private void cleanup(Player player) {
    removeNimble(player);
    wingedFallProtection.remove(player.getUniqueId());
    wingedCooldowns.remove(player.getUniqueId());
    shortbowCooldowns.remove(player.getUniqueId());
  }
  @SuppressWarnings("deprecation") private void removeNimble(Player player) {
    AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
    if (attribute != null) attribute.getModifiers().stream().filter(modifier -> modifier.getUniqueId().equals(NIMBLE_MODIFIER)).findFirst().ifPresent(attribute::removeModifier);
  }
  private static boolean survival(Player player) { return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE; }
}
