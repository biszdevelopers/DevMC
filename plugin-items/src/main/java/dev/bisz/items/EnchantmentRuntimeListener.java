package dev.bisz.items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Central, opt-in dispatcher for custom item/enchantment ticking and damage hooks. */
final class EnchantmentRuntimeListener implements Listener {
  private final ItemsPlugin plugin;
  private final ItemFactory factory;
  private final ItemRegistry items;
  private final EnchantmentRegistry enchantments;
  private final Map<UUID, ItemStack> projectileSources = new HashMap<>();

  EnchantmentRuntimeListener(ItemsPlugin plugin, ItemFactory factory, ItemRegistry items, EnchantmentRegistry enchantments) {
    this.plugin = plugin; this.factory = factory; this.items = items; this.enchantments = enchantments;
    plugin.getServer().getScheduler().runTaskTimer((Plugin) plugin, this::tick, 1L, 1L);
  }

  private void tick() {
    if (!items.hasTickingDefinitions() && !enchantments.hasTickingDefinitions()) return;
    for (Player player : plugin.getServer().getOnlinePlayers()) {
      int selected = player.getInventory().getHeldItemSlot();
      tickHand(player, selected, EquipmentSlot.HAND);
      tickHand(player, 40, EquipmentSlot.OFF_HAND);
      for (int slot = 0; slot <= 40; slot++) if (slot != selected && slot != 40) tickInventory(player, slot);
    }
  }
  private void tickHand(Player player, int slot, EquipmentSlot hand) {
    ItemStack item = player.getInventory().getItem(slot); if (item == null || item.getType().isAir()) return;
    try {
      DevItemStack stack = factory.wrap(item);
      if (!stack.definition().vanilla() && stack.definition().properties().handTicking()) stack.definition().handTick(stack, player, hand);
      for (Map.Entry<DevEnchantment, Integer> entry : stack.enchantments().entrySet()) {
        DevEnchantment enchantment = entry.getKey();
        if (!enchantment.vanilla() && enchantment.properties().handTicking()) enchantment.handTick(stack, entry.getValue(), player, hand);
      }
      player.getInventory().setItem(slot, stack.bukkitStack());
    } catch (RuntimeException exception) { log("hand tick", exception); }
  }
  private void tickInventory(Player player, int slot) {
    ItemStack item = player.getInventory().getItem(slot); if (item == null || item.getType().isAir()) return;
    try {
      DevItemStack stack = factory.wrap(item);
      if (!stack.definition().vanilla() && stack.definition().properties().inventoryTicking()) stack.definition().inventoryTick(stack, player, slot);
      for (Map.Entry<DevEnchantment, Integer> entry : stack.enchantments().entrySet()) {
        DevEnchantment enchantment = entry.getKey();
        if (!enchantment.vanilla() && enchantment.properties().inventoryTicking()) enchantment.inventoryTick(stack, entry.getValue(), player, slot);
      }
      player.getInventory().setItem(slot, stack.bukkitStack());
    } catch (RuntimeException exception) { log("inventory tick", exception); }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  void outgoingDamage(EntityDamageByEntityEvent event) {
    Player holder = null; ItemStack source = null; boolean projectile = event.getDamager() instanceof Projectile;
    if (event.getDamager() instanceof Player player) { holder = player; source = player.getInventory().getItemInMainHand(); }
    else if (event.getDamager() instanceof Projectile shot && shot.getShooter() instanceof Player player) { holder = player; source = projectileSources.get(shot.getUniqueId()); }
    if (holder == null || source == null || source.getType().isAir()) return;
    applyDamage(event, holder, event.getEntity(), source, false, projectile);
  }
  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  void incomingDamage(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Player player)) return;
    Entity other = event instanceof EntityDamageByEntityEvent by ? by.getDamager() : null;
    boolean projectile = other instanceof Projectile;
    for (int slot : new int[] {36, 37, 38, 39, player.getInventory().getHeldItemSlot(), 40}) {
      ItemStack source = player.getInventory().getItem(slot);
      if (source != null && !source.getType().isAir()) applyDamage(event, player, other, source, true, projectile);
    }
  }
  private void applyDamage(EntityDamageEvent event, Player holder, Entity other, ItemStack item, boolean incoming, boolean projectile) {
    try {
      DevItemStack stack = factory.wrap(item); double damage = event.getDamage();
      for (Map.Entry<DevEnchantment, Integer> entry : stack.enchantments().entrySet()) {
        DevEnchantment enchantment = entry.getKey(); if (enchantment.vanilla()) continue;
        EnchantmentDamageContext context = new EnchantmentDamageContext(event, holder, other, stack, entry.getValue(), incoming, projectile);
        double changed = incoming ? enchantment.incomingDamage(context, damage) : enchantment.outgoingDamage(context, damage);
        if (Double.isFinite(changed)) damage = Math.max(0.0D, changed);
      }
      event.setDamage(damage);
    } catch (RuntimeException exception) { log("damage modifier", exception); }
  }
  @EventHandler(ignoreCancelled = true)
  void captureBow(EntityShootBowEvent event) {
    if (event.getEntity() instanceof Player player && event.getProjectile() instanceof Projectile projectile) projectileSources.put(projectile.getUniqueId(), event.getBow().clone());
  }
  @EventHandler(ignoreCancelled = true)
  void captureProjectile(ProjectileLaunchEvent event) {
    if (event.getEntity().getShooter() instanceof Player player) projectileSources.putIfAbsent(event.getEntity().getUniqueId(), player.getInventory().getItemInMainHand().clone());
  }
  @EventHandler void clearProjectile(ProjectileHitEvent event) { projectileSources.remove(event.getEntity().getUniqueId()); }
  private void log(String operation, RuntimeException exception) { plugin.getLogger().warning("Could not run ItemLib " + operation + ": " + exception.getMessage()); }
}
