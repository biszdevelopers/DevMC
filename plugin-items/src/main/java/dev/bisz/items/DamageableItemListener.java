package dev.bisz.items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.EntityEffect;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/** Runtime safety net for {@link OverrideDamageableVanillaItem}. */
final class DamageableItemListener implements Listener {
  private final ItemsPlugin plugin;
  private final ItemFactory factory;
  private final Map<UUID, Long> warned = new HashMap<>();

  DamageableItemListener(ItemsPlugin plugin, ItemFactory factory) { this.plugin = plugin; this.factory = factory; }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  void damage(PlayerItemDamageEvent event) {
    DevItemStack stack = wrap(event.getItem());
    if (!(stack != null && stack.definition() instanceof OverrideDamageableVanillaItem definition)) return;
    if (definition.broken(stack)) { event.setCancelled(true); return; }
    if (event.getDamage() < definition.remainingDurability(stack)) {
      plugin.getServer().getScheduler().runTask(plugin, () -> stack.render(event.getPlayer()));
      return;
    }
    event.setCancelled(true);
    ItemMeta meta = stack.bukkitStack().getItemMeta();
    if (meta instanceof Damageable damageable) damageable.setDamage(Math.max(0, definition.material().getMaxDurability() - 1));
    if (meta != null) stack.bukkitStack().setItemMeta(meta);
    definition.broken(stack, true);
    EquipmentSlot slot = slot(event.getPlayer(), event.getItem());
    breakEffect(event.getPlayer(), slot);
    evictBrokenArmor(event.getPlayer(), event.getItem(), slot);
    event.getPlayer().sendMessage(ItemTranslations.translate(ItemTranslations.language(event.getPlayer()),
      "itemmeta.broken", "§cYour %s is broken and must be mended.", ItemTranslations.humanize(stack.definition().id().path())));
    stack.render(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  void mend(PlayerItemMendEvent event) {
    DevItemStack stack = wrap(event.getItem());
    if (!(stack != null && stack.definition() instanceof OverrideDamageableVanillaItem definition)) return;
    if (event.getRepairAmount() > 0) definition.prepareMend(stack);
    plugin.getServer().getScheduler().runTask(plugin, () -> stack.render(event.getPlayer()));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  void attack(EntityDamageByEntityEvent event) {
    if (event.getDamager() instanceof Player player && broken(player.getInventory().getItemInMainHand(), player)) deny(player, event);
  }
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  void breakBlock(BlockBreakEvent event) { if (broken(event.getPlayer().getInventory().getItemInMainHand(), event.getPlayer())) deny(event.getPlayer(), event); }
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  void interact(PlayerInteractEvent event) { if (broken(event.getItem(), event.getPlayer())) deny(event.getPlayer(), event); }
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  void shoot(EntityShootBowEvent event) { if (event.getEntity() instanceof Player player && broken(event.getBow(), player)) deny(player, event); }
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  void fish(PlayerFishEvent event) { if (broken(event.getPlayer().getInventory().getItemInMainHand(), event.getPlayer())) deny(event.getPlayer(), event); }
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  void dispenseArmor(BlockDispenseArmorEvent event) { if (broken(event.getItem(), null)) event.setCancelled(true); }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  void armorClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) return;
    boolean armorTarget = event.getSlotType() == InventoryType.SlotType.ARMOR;
    ItemStack hotbar = event.getHotbarButton() >= 0 ? player.getInventory().getItem(event.getHotbarButton()) : null;
    boolean shiftEquip = event.isShiftClick() && event.getClickedInventory() instanceof PlayerInventory
      && event.getView().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING
      && brokenArmor(event.getCurrentItem(), player);
    if ((armorTarget && (brokenArmor(event.getCursor(), player) || brokenArmor(hotbar, player))) || shiftEquip) {
      event.setCancelled(true);
      denyArmor(player);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  void armorDrag(InventoryDragEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) return;
    boolean invalid = event.getNewItems().entrySet().stream().anyMatch(entry ->
      event.getView().getSlotType(entry.getKey()) == InventoryType.SlotType.ARMOR && brokenArmor(entry.getValue(), player));
    if (invalid) { event.setCancelled(true); denyArmor(player); }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void reconcileAfterClick(InventoryClickEvent event) {
    if (event.getWhoClicked() instanceof Player player)
      plugin.getServer().getScheduler().runTask(plugin, () -> reconcileArmor(player));
  }
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void reconcileAfterDrag(InventoryDragEvent event) {
    if (event.getWhoClicked() instanceof Player player)
      plugin.getServer().getScheduler().runTask(plugin, () -> reconcileArmor(player));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  void join(PlayerJoinEvent event) { plugin.getServer().getScheduler().runTask(plugin, () -> reconcileArmor(event.getPlayer())); }
  @EventHandler(priority = EventPriority.MONITOR)
  void respawn(PlayerRespawnEvent event) { plugin.getServer().getScheduler().runTask(plugin, () -> reconcileArmor(event.getPlayer())); }

  private boolean broken(ItemStack item, Player viewer) {
    DevItemStack stack = wrap(item);
    return stack != null && stack.definition() instanceof OverrideDamageableVanillaItem definition && definition.broken(stack);
  }
  private boolean brokenArmor(ItemStack item, Player viewer) {
    return item != null && switch (item.getType().getEquipmentSlot()) {
      case HEAD, CHEST, LEGS, FEET -> broken(item, viewer);
      default -> false;
    };
  }
  private DevItemStack wrap(ItemStack item) { try { return item == null || item.getType().isAir() ? null : factory.wrap(item); } catch (RuntimeException ignored) { return null; } }
  private void deny(Player player, org.bukkit.event.Cancellable event) {
    event.setCancelled(true);
    long now = System.currentTimeMillis();
    if (now - warned.getOrDefault(player.getUniqueId(), 0L) < 1000L) return;
    warned.put(player.getUniqueId(), now);
    player.sendMessage(ItemTranslations.translate(ItemTranslations.language(player), "itemmeta.broken.cannot_use", "§cThis item is broken and must be mended before it can be used."));
  }
  private static EquipmentSlot slot(Player player, ItemStack item) {
    PlayerInventory inventory = player.getInventory();
    if (same(inventory.getItemInMainHand(), item)) return EquipmentSlot.HAND;
    if (same(inventory.getItemInOffHand(), item)) return EquipmentSlot.OFF_HAND;
    if (same(inventory.getHelmet(), item)) return EquipmentSlot.HEAD;
    if (same(inventory.getChestplate(), item)) return EquipmentSlot.CHEST;
    if (same(inventory.getLeggings(), item)) return EquipmentSlot.LEGS;
    return same(inventory.getBoots(), item) ? EquipmentSlot.FEET : EquipmentSlot.HAND;
  }
  private static boolean same(ItemStack left, ItemStack right) { return left != null && right != null && left == right || left != null && left.isSimilar(right); }
  private static EntityEffect effect(EquipmentSlot slot) { return switch (slot) {
    case HEAD -> EntityEffect.BREAK_EQUIPMENT_HELMET; case CHEST -> EntityEffect.BREAK_EQUIPMENT_CHESTPLATE;
    case LEGS -> EntityEffect.BREAK_EQUIPMENT_LEGGINGS; case FEET -> EntityEffect.BREAK_EQUIPMENT_BOOTS;
    case OFF_HAND -> EntityEffect.BREAK_EQUIPMENT_OFF_HAND; default -> EntityEffect.BREAK_EQUIPMENT_MAIN_HAND;
  }; }
  private static void breakEffect(Player player, EquipmentSlot slot) {
    player.playEffect(effect(slot));
    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1F, 1F);
  }
  private static void evictBrokenArmor(Player player, ItemStack item, EquipmentSlot slot) {
    if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) return;
    PlayerInventory inventory = player.getInventory();
    switch (slot) { case HEAD -> inventory.setHelmet(null); case CHEST -> inventory.setChestplate(null); case LEGS -> inventory.setLeggings(null); case FEET -> inventory.setBoots(null); default -> { return; } }
    if (!inventory.addItem(item).isEmpty()) player.getWorld().dropItemNaturally(player.getLocation(), item);
    player.sendMessage(ItemTranslations.translate(ItemTranslations.language(player), "itemmeta.broken.cannot_equip", "§cBroken armor cannot be equipped."));
  }
  private void denyArmor(Player player) {
    long now = System.currentTimeMillis();
    if (now - warned.getOrDefault(player.getUniqueId(), 0L) < 1000L) return;
    warned.put(player.getUniqueId(), now);
    player.sendMessage(ItemTranslations.translate(ItemTranslations.language(player), "itemmeta.broken.cannot_equip", "§cBroken armor cannot be equipped."));
  }
  private void reconcileArmor(Player player) {
    PlayerInventory inventory = player.getInventory();
    for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
      ItemStack item = switch (slot) { case HEAD -> inventory.getHelmet(); case CHEST -> inventory.getChestplate(); case LEGS -> inventory.getLeggings(); case FEET -> inventory.getBoots(); default -> null; };
      if (brokenArmor(item, player)) evictBrokenArmor(player, item, slot);
    }
  }
}
