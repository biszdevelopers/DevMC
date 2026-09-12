/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  dev.bisz.menus.MenuInventoryHolder
 *  dev.bisz.players.locales.PlayerLocaleUpdateEvent
 *  org.bukkit.ChatColor
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.BlockPlaceEvent
 *  org.bukkit.event.entity.EntityPickupItemEvent
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryEvent
 *  org.bukkit.event.inventory.InventoryOpenEvent
 *  org.bukkit.event.inventory.PrepareAnvilEvent
 *  org.bukkit.event.inventory.PrepareGrindstoneEvent
 *  org.bukkit.event.inventory.PrepareItemCraftEvent
 *  org.bukkit.event.inventory.PrepareSmithingEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.plugin.Plugin
 */
package dev.bisz.items;

import dev.bisz.items.DevItemStack;
import dev.bisz.items.ItemFactory;
import dev.bisz.items.ItemTranslations;
import dev.bisz.items.ItemsPlugin;
import dev.bisz.menus.MenuInventoryHolder;
import dev.bisz.players.locales.PlayerLocaleUpdateEvent;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

final class ItemRuntimeListener implements Listener {

  private final ItemsPlugin plugin;
  private final ItemFactory factory;

  ItemRuntimeListener(ItemsPlugin plugin, ItemFactory factory) {
    this.plugin = plugin;
    this.factory = factory;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  void onLocaleUpdate(PlayerLocaleUpdateEvent event) {
    this.refreshInventory(event.player());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  void onJoin(PlayerJoinEvent event) {
    this.plugin.getServer()
      .getScheduler()
      .runTask((Plugin) this.plugin, () -> {
        if (event.getPlayer().isOnline()) {
          this.refreshInventory(event.getPlayer());
        }
      });
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  void onPickup(EntityPickupItemEvent event) {
    LivingEntity livingEntity = event.getEntity();
    if (!(livingEntity instanceof Player)) {
      return;
    }
    Player player = (Player) livingEntity;
    ItemStack rendered = this.refresh(event.getItem().getItemStack(), player);
    if (rendered != null) {
      event.getItem().setItemStack(rendered);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  void onMove(PlayerMoveEvent event) {
    if (
      event.getTo() == null ||
      (event.getFrom().getX() == event.getTo().getX() &&
        event.getFrom().getY() == event.getTo().getY() &&
        event.getFrom().getZ() == event.getTo().getZ()) ||
      event.getPlayer().getInventory().firstEmpty() >= 0
    ) return;

    Player player = event.getPlayer();
    for (var entity : player.getNearbyEntities(1.5, 1.0, 1.5)) {
      if (!(entity instanceof Item dropped)) continue;
      ItemStack ground = dropped.getItemStack();
      if (!hasVanillaStackSpace(player, ground)) continue;
      ItemStack rendered = refresh(ground, player);
      if (rendered != null) dropped.setItemStack(rendered);
    }
  }

  private boolean hasVanillaStackSpace(Player player, ItemStack ground) {
    DevItemStack dropped;
    try {
      dropped = factory.wrap(ground);
    } catch (RuntimeException exception) {
      return false;
    }
    if (!dropped.definition().vanilla()) return false;
    for (ItemStack current : player.getInventory().getStorageContents()) {
      if (
        current == null ||
        current.getType() != ground.getType() ||
        current.getAmount() >= current.getMaxStackSize()
      ) continue;
      try {
        if (
          factory
            .wrap(current)
            .definition()
            .id()
            .equals(dropped.definition().id())
        ) return true;
      } catch (RuntimeException ignored) {}
    }
    return false;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  void onInventoryOpen(InventoryOpenEvent event) {
    HumanEntity humanEntity = event.getPlayer();
    if (!(humanEntity instanceof Player)) {
      return;
    }
    Player player = (Player) humanEntity;
    this.refreshInventory(player);
    Inventory opened = event.getInventory();
    if (!(opened.getHolder() instanceof MenuInventoryHolder)) {
      this.refreshInventory(opened, player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onInventoryClick(InventoryClickEvent event) {
    HumanEntity humanEntity = event.getWhoClicked();
    if (!(humanEntity instanceof Player)) {
      return;
    }
    Player player = (Player) humanEntity;
    this.plugin.getServer()
      .getScheduler()
      .runTask((Plugin) this.plugin, () -> {
        if (!player.isOnline()) {
          return;
        }
        this.refreshInventory(player);
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof MenuInventoryHolder)) {
          this.refreshInventory(top, player);
        }
      });
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  void onCraft(PrepareItemCraftEvent event) {
    Player player = ItemRuntimeListener.viewer((InventoryEvent) event);
    ItemStack rendered = this.refresh(event.getInventory().getResult(), player);
    if (rendered != null) {
      event.getInventory().setResult(rendered);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  void onAnvil(PrepareAnvilEvent event) {
    Player player = ItemRuntimeListener.viewer((InventoryEvent) event);
    ItemStack result = this.mergeCustomAnvilEnchantments(event);
    if (result == null || result.getType().isAir()) {
      return;
    }
    try {
      DevItemStack resolved = this.factory.wrap(result);
      this.preserveAnvilName(event, resolved);
      // Anvil output may change enchantments without changing the viewer locale.
      resolved.render(player);
      event.setResult(resolved.bukkitStack());
    } catch (RuntimeException exception) {
      this.logFailure(result, exception);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onEnchant(EnchantItemEvent event) {
    this.plugin.getServer().getScheduler().runTask((Plugin) this.plugin, () -> {
      Player player = event.getEnchanter();
      if (!player.isOnline()) return;
      try {
        DevItemStack stack = this.factory.wrap(event.getItem());
        stack.render(player);
      } catch (RuntimeException exception) {
        this.logFailure(event.getItem(), exception);
      }
    });
  }

  /** Preserves left custom data and applies ItemLib's enchanted-book compatibility rules. */
  private ItemStack mergeCustomAnvilEnchantments(PrepareAnvilEvent event) {
    ItemStack left = event.getInventory().getItem(0);
    ItemStack right = event.getInventory().getItem(1);
    ItemStack nativeResult = event.getResult();
    if (left == null || left.getType().isAir()) return nativeResult;
    try {
      DevItemStack leftStack = this.factory.wrap(left);
      DevItemStack rightStack = right == null || right.getType().isAir() ? null : this.factory.wrap(right);
      boolean enchantedBook = right != null && right.getType() == Material.ENCHANTED_BOOK && rightStack != null;
      if (enchantedBook && denyBowMerge(left.getType(), leftStack.enchantments(), rightStack.enchantments())) {
        event.setResult(null);
        return null;
      }
      if (nativeResult == null || nativeResult.getType().isAir()) {
        if (!enchantedBook || rightStack.enchantmentData().keySet().stream().noneMatch(enchantment ->
          compatibleWithTarget(enchantment, left)
        )) return nativeResult;
        nativeResult = left.clone();
      }
      DevItemStack output = this.factory.wrap(nativeResult);
      output.copyItemIdentityFrom(leftStack);
      output.copyCustomEnchantmentsFrom(leftStack);
      if (rightStack == null || (!enchantedBook && event.getResult() == null)) return output.bukkitStack();
      int changed = 0;
      Map<DevEnchantment, EnchantmentData> incoming = enchantedBook
        ? rightStack.enchantmentData()
        : rightStack.customEnchantments().entrySet().stream().collect(java.util.stream.Collectors.toMap(
          Map.Entry::getKey,
          entry -> new EnchantmentData(entry.getValue()),
          (leftValue, rightValue) -> leftValue,
          java.util.LinkedHashMap::new
        ));
      for (var entry : incoming.entrySet()) {
        DevEnchantment enchantment = entry.getKey();
        if (enchantedBook && !compatibleWithTarget(enchantment, left)) {
          restoreLeftEnchantment(output, leftStack, enchantment);
          continue;
        }
        int previous = leftStack.enchantmentLevel(enchantment).orElse(0);
        int incomingLevel = entry.getValue().level();
        int merged = previous == 0
          ? incomingLevel
          : previous == incomingLevel ? Math.min(3999, previous + 1) : Math.max(previous, incomingLevel);
        int current = output.enchantmentLevel(enchantment).orElse(0);
        if (merged != current) {
          output.enchant(enchantment, merged);
          if (merged > previous) changed++;
        }
      }
      if (changed > 0) {
        int cost = event.getInventory().getRepairCost();
        event.getInventory().setRepairCost((event.getResult() == null || event.getResult().getType().isAir()) ? Math.max(1, changed) : cost + changed);
      }
      output.mergeAnvilEnchantmentMetadata(leftStack, rightStack);
      return output.bukkitStack();
    } catch (RuntimeException exception) {
      this.logFailure(left, exception);
      return nativeResult;
    }
  }

  static boolean compatibleWithTarget(DevEnchantment enchantment, ItemStack target) {
    if (!(enchantment instanceof VanillaEnchantment vanilla)) return true;
    if (target.getType() == Material.BOOK || target.getType() == Material.ENCHANTED_BOOK) return true;
    return vanilla.bukkit().canEnchantItem(target);
  }

  private static void restoreLeftEnchantment(
    DevItemStack output,
    DevItemStack left,
    DevEnchantment enchantment
  ) {
    Integer level = left.enchantmentLevel(enchantment).orElse(null);
    if (level == null) output.removeEnchantment(enchantment);
    else output.enchant(enchantment, level, left.enchantmentMetadata(enchantment));
  }

  static boolean denyBowMerge(
    Material target,
    Map<DevEnchantment, Integer> left,
    Map<DevEnchantment, Integer> right
  ) {
    if (target != Material.BOW) return false;
    boolean mending = hasEnchantment(left, "minecraft", "mending") || hasEnchantment(right, "minecraft", "mending");
    boolean infinity = hasEnchantment(left, "minecraft", "infinity") || hasEnchantment(right, "minecraft", "infinity");
    return mending && infinity;
  }

  private static boolean hasEnchantment(
    Map<DevEnchantment, Integer> enchantments,
    String namespace,
    String path
  ) {
    return enchantments.keySet().stream().anyMatch(enchantment ->
      enchantment.id().namespace().equals(namespace) && enchantment.id().path().equals(path)
    );
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  void onGrindstone(PrepareGrindstoneEvent event) {
    ItemStack result = event.getResult();
    if (result == null || result.getType().isAir()) return;
    try {
      DevItemStack resolved = this.factory.wrap(result);
      resolved.clearEnchantmentMetadata();
      resolved.render(ItemRuntimeListener.viewer((InventoryEvent) event));
      event.setResult(resolved.bukkitStack());
    } catch (RuntimeException exception) {
      this.logFailure(result, exception);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  void onSmithing(PrepareSmithingEvent event) {
    ItemStack rendered = this.refresh(
      event.getResult(),
      ItemRuntimeListener.viewer((InventoryEvent) event)
    );
    if (rendered != null) {
      event.setResult(rendered);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  void onPlace(BlockPlaceEvent event) {
    try {
      DevItemStack stack = this.factory.wrap(event.getItemInHand());
      if (!stack.definition().properties().placeable()) {
        event.setCancelled(true);
        event
          .getPlayer()
          .sendMessage(
            ItemTranslations.translate(
              ItemTranslations.language(event.getPlayer()),
              "itemmeta.cannot_place",
              "This item cannot be placed.",
              new Object[0]
            )
          );
      }
    } catch (RuntimeException exception) {
      this.logFailure(event.getItemInHand(), exception);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onConsume(PlayerItemConsumeEvent event) {
    try {
      DevItemStack stack = this.factory.wrap(event.getItem());
      stack.definition().consume(stack, event.getPlayer());
    } catch (RuntimeException exception) {
      this.logFailure(event.getItem(), exception);
    }
  }

  private void preserveAnvilName(PrepareAnvilEvent event, DevItemStack result) {
    String visible;
    String rename = event.getInventory().getRenameText();
    ItemStack input = event.getInventory().getItem(0);
    if (rename == null || rename.isBlank()) {
      result.customName(null);
      return;
    }
    if (input == null || input.getType().isAir()) {
      return;
    }
    DevItemStack original = this.factory.wrap(input);
    String string = visible = input.hasItemMeta() &&
      input.getItemMeta().hasDisplayName()
      ? ChatColor.stripColor((String) input.getItemMeta().getDisplayName())
      : "";
    if (original.customName().isPresent() || !rename.equals(visible)) {
      result.customName(rename);
    }
  }

  private void refreshInventory(Player player) {
    this.refreshInventory((Inventory) player.getInventory(), player);
  }

  private void refreshInventory(Inventory inventory, Player player) {
    for (int slot = 0; slot < inventory.getSize(); ++slot) {
      ItemStack current = inventory.getItem(slot);
      ItemStack rendered = this.refresh(current, player);
      if (rendered == null || rendered == current) continue;
      inventory.setItem(slot, rendered);
    }
  }

  private ItemStack refresh(ItemStack stack, Player viewer) {
    if (stack == null || stack.getType().isAir()) {
      return stack;
    }
    if (
      viewer == null || this.factory.isRenderingSuppressed(stack)
    ) return stack;
    try {
      return this.factory.refreshIfLocaleChanged(stack, viewer).bukkitStack();
    } catch (RuntimeException exception) {
      this.logFailure(stack, exception);
      return stack;
    }
  }

  private void logFailure(ItemStack stack, RuntimeException exception) {
    this.plugin.getLogger().warning(
      "Could not render " +
      String.valueOf(stack.getType()) +
      " for locale: " +
      exception.getMessage()
    );
  }

  private static Player viewer(InventoryEvent event) {
    return event
      .getViewers()
      .stream()
      .filter(Player.class::isInstance)
      .map(Player.class::cast)
      .findFirst()
      .orElse(null);
  }
}
