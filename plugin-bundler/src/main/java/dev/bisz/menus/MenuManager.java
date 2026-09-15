package dev.bisz.menus;

import dev.bisz.menus.internal.BundlerMenuHolder;
import dev.bisz.players.locales.Locale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Centralized owner of all Bundler menu sessions, event routing, storage transactions,
 * and generated-item cleanup.
 */
public final class MenuManager implements Listener {

  private static final String MENU_LABEL = "MENU ITEM";
  private static final long WARNING_INTERVAL_MILLIS = 10_000L;

  private final Plugin plugin;
  private final NamespacedKey menuItemKey;
  private final NamespacedKey menuSessionKey;
  private final Map<UUID, ActiveMenu> sessions = new HashMap<>();
  private final Map<UUID, UUID> sessionsByPlayer = new HashMap<>();
  private final Map<UUID, Long> lastWarning = new HashMap<>();
  private final BukkitTask cleanupTask;

  /** Creates and registers a menu manager owned by the supplied plugin. */
  public MenuManager(Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.menuItemKey = new NamespacedKey(plugin, "menu_item");
    this.menuSessionKey = new NamespacedKey(plugin, "menu_session");
    Bukkit.getPluginManager().registerEvents(this, plugin);
    this.cleanupTask = Bukkit.getScheduler().runTaskTimer(
      plugin,
      this::periodicCleanup,
      200L,
      200L
    );
  }

  /** Opens a template without storage mappings. */
  public MenuSession open(Player player, MenuTemplate template) {
    return openResolved(player, template, null);
  }

  /** Opens a template with a real or temporary Bukkit inventory adapter. */
  public MenuSession open(
    Player player,
    MenuTemplate template,
    Inventory backingStorage
  ) {
    return openResolved(
      player,
      template,
      backingStorage == null
        ? null
        : StorageProvider.fromInventory(backingStorage)
    );
  }

  /** Opens a template with scalable indexed storage. */
  public MenuSession open(
    Player player,
    MenuTemplate template,
    StorageProvider storageProvider
  ) {
    return openResolved(player, template, storageProvider);
  }

  private MenuSession openResolved(
    Player player,
    MenuTemplate template,
    StorageProvider suppliedStorage
  ) {
    requirePrimaryThread();
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(template, "template");
    StorageProvider boundStorage = template.boundStorage(player);
    if (boundStorage != null && suppliedStorage != null) {
      throw new IllegalArgumentException(
        "This template already binds its StorageProvider"
      );
    }
    StorageProvider storageProvider = boundStorage != null
      ? boundStorage
      : suppliedStorage;
    if (template.requiresStorage() && storageProvider == null) {
      throw new IllegalArgumentException(
        "This template requires a StorageProvider"
      );
    }
    if (!template.requiresStorage() && storageProvider != null) {
      throw new IllegalArgumentException(
        "A content-only template cannot be opened with a StorageProvider"
      );
    }
    UUID previous = sessionsByPlayer.get(player.getUniqueId());
    if (previous != null) {
      ActiveMenu old = sessions.get(previous);
      if (old != null) finish(old, null);
    }
    int removed = purgePlayer(player);
    reportCleanup(player, removed);
    UUID id = UUID.randomUUID();
    MenuSession session = new MenuSession(this, player.getUniqueId(), id);
    RenderedMenuPage rendered = template.render(player, 1, storageProvider);
    if (storageProvider != null) validateStorage(
      rendered.storageSlots(),
      storageProvider
    );
    Inventory inventory = Bukkit.createInventory(
      new BundlerMenuHolder(id),
      template.rows() * 9,
      template.title(player)
    );
    ActiveMenu active = new ActiveMenu(
      session,
      player,
      template,
      storageProvider,
      inventory,
      rendered
    );
    sessions.put(id, active);
    sessionsByPlayer.put(player.getUniqueId(), id);
    render(active);
    player.openInventory(inventory);
    invoke(
      active,
      "open callback",
      template.openAction(),
      new MenuOpenContext(player, session)
    );
    return session;
  }

  /** Opens the compatibility menu definition as a single-page template. */
  @SuppressWarnings("deprecation")
  public MenuSession open(Player player, MenuDefinition definition) {
    SinglePageMenuTemplate.Builder builder = SinglePageMenuTemplate.builder(
      definition.title(),
      definition.rows()
    );
    definition
      .items()
      .forEach((slot, stack) -> {
        MenuItem item = MenuItem.builder(stack).build();
        Consumer<MenuClickContext> action = definition
          .clickHandlers()
          .get(slot);
        if (action != null) item = item.onClick(action);
        builder.item(slot, item);
      });
    return open(player, builder.build());
  }

  /** Returns whether an item is a generated menu-view clone. */
  public boolean isMenuItem(ItemStack item) {
    if (
      item == null || item.getType().isAir() || !item.hasItemMeta()
    ) return false;
    return MENU_LABEL.equals(
      item
        .getItemMeta()
        .getPersistentDataContainer()
        .get(menuItemKey, PersistentDataType.STRING)
    );
  }

  /** Removes generated menu clones from all player-owned slots and returns the removed amount. */
  public int purgeMenuItems(Player player) {
    requirePrimaryThread();
    int removed = purgePlayer(Objects.requireNonNull(player, "player"));
    reportCleanup(player, removed);
    return removed;
  }

  /** Closes all menus and stops the periodic cleanup task. */
  public void dispose() {
    requirePrimaryThread();
    for (ActiveMenu active : new ArrayList<>(sessions.values()))
      finish(active, null);
    cleanupTask.cancel();
    HandlerList.unregisterAll(this);
    sessions.clear();
    sessionsByPlayer.clear();
    lastWarning.clear();
  }

  int page(MenuSession session) {
    return requireActive(session).rendered.page();
  }

  void setItem(MenuSession session, int slot, MenuItem item) {
    requirePrimaryThread();
    ActiveMenu active = requireActive(session);
    validateMenuSlot(active.template, slot);
    active.liveItems.put(slot, item);
    active.inventory.setItem(
      slot,
      tag(item.render(active.player), active.session.sessionId())
    );
  }

  void removeItem(MenuSession session, int slot) {
    requirePrimaryThread();
    ActiveMenu active = requireActive(session);
    validateMenuSlot(active.template, slot);
    active.liveItems.remove(slot);
    active.inventory.clear(slot);
  }

  void refresh(MenuSession session) {
    requirePrimaryThread();
    ActiveMenu active = requireActive(session);
    active.rendered = active.template.render(
      active.player,
      active.rendered.page(),
      active.storage
    );
    if (active.storage != null) validateStorage(
      active.rendered.storageSlots(),
      active.storage
    );
    render(active);
  }

  void changePage(MenuSession session, int delta) {
    requirePrimaryThread();
    ActiveMenu active = requireActive(session);
    int previous = active.rendered.page();
    RenderedMenuPage target = active.template.render(
      active.player,
      previous + delta,
      active.storage
    );
    if (active.storage != null) validateStorage(
      target.storageSlots(),
      active.storage
    );
    if (target.page() == previous) return;
    active.rendered = target;
    render(active);
    invoke(
      active,
      "page callback",
      active.template.pageChangeAction(),
      new MenuPageChangeContext(
        active.player,
        active.session,
        previous,
        target.page()
      )
    );
  }

  void close(MenuSession session) {
    requirePrimaryThread();
    ActiveMenu active = requireActive(session);
    if (
      active.player.isOnline() &&
      active.player.getOpenInventory().getTopInventory() == active.inventory
    ) {
      active.player.closeInventory();
    }
    if (sessions.containsKey(session.sessionId())) finish(active, null);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onClick(InventoryClickEvent event) {
    ActiveMenu active = active(event.getView().getTopInventory().getHolder());
    if (active == null) return;
    event.setCancelled(true);
    if (
      !(event.getWhoClicked() instanceof Player player) ||
      player != active.player
    ) return;
    if (isInvalidShiftPlacement(
      event.isShiftClick(),
      event.getCurrentItem(),
      event.getCursor()
    )) {
      player.setItemOnCursor(clean(event.getCursor()));
      render(active);
      Bukkit.getScheduler().runTask(plugin, player::updateInventory);
      return;
    }
    if (isMenuItem(player.getItemOnCursor())) {
      player.setItemOnCursor(null);
      reportCleanup(player, 1);
      return;
    }
    int rawSlot = event.getRawSlot();
    int topSize = active.inventory.getSize();
    if (rawSlot < 0) return;
    if (rawSlot >= topSize) {
      handleBottomClick(active, event);
      return;
    }
    if (Objects.equals(active.rendered.previousSlot(), rawSlot)) {
      changePage(active.session, -1);
      return;
    }
    if (Objects.equals(active.rendered.nextSlot(), rawSlot)) {
      changePage(active.session, 1);
      return;
    }
    StorageSlot storageSlot = active.rendered.storageSlots().get(rawSlot);
    if (storageSlot != null) {
      if (!storageSlot.readOnly()) transactStorageClick(
        active,
        storageSlot,
        event
      );
      invokeUnhandled(
        active,
        rawSlot,
        storageSlot.storageSlot(),
        active.storage,
        event
      );
      return;
    }
    MenuItem item = active.liveItems.get(rawSlot);
    if (item != null && item.clickAction() != null) {
      invoke(
        active,
        "item click callback at slot " + rawSlot,
        item.clickAction(),
        new MenuClickContext(player, active.session, rawSlot, null, null, event)
      );
    } else {
      invokeUnhandled(active, rawSlot, null, null, event);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onDrag(InventoryDragEvent event) {
    ActiveMenu active = active(event.getView().getTopInventory().getHolder());
    if (active == null) return;
    int topSize = active.inventory.getSize();
    boolean touchesTop = event
      .getRawSlots()
      .stream()
      .anyMatch(slot -> slot < topSize);
    if (!touchesTop) return;
    event.setCancelled(true);
    for (int rawSlot : event.getRawSlots()) {
      if (rawSlot >= topSize) continue;
      StorageSlot mapping = active.rendered.storageSlots().get(rawSlot);
      if (
        mapping == null || mapping.access() != StorageAccess.READ_WRITE
      ) return;
    }
    LinkedHashMap<StorageSlot, ItemStack> previousValues =
      new LinkedHashMap<>();
    LinkedHashMap<StorageSlot, ItemStack> newValues = new LinkedHashMap<>();
    for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
      if (entry.getKey() >= topSize) continue;
      StorageSlot mapping = active.rendered.storageSlots().get(entry.getKey());
      ItemStack before = readStorage(active, mapping);
      ItemStack value = clean(entry.getValue());
      previousValues.put(mapping, before);
      newValues.put(mapping, value);
      if (!writeStorage(active, mapping, value)) {
        previousValues.forEach((changed, original) ->
          writeStorage(active, changed, original)
        );
        render(active);
        return;
      }
    }
    newValues.forEach((mapping, value) ->
      storageChanged(active, mapping, previousValues.get(mapping), value)
    );
    for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
      if (entry.getKey() >= topSize) event
        .getView()
        .setItem(entry.getKey(), clean(entry.getValue()));
    }
    event.getWhoClicked().setItemOnCursor(clean(event.getCursor()));
    render(active);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onOpen(InventoryOpenEvent event) {
    if (!(event.getPlayer() instanceof Player player)) return;
    int removed = purgePlayer(player);
    if (!(event.getInventory().getHolder() instanceof MenuInventoryHolder)) {
      removed += purgeInventory(event.getInventory());
    }
    reportCleanup(player, removed);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onClose(InventoryCloseEvent event) {
    ActiveMenu active = active(event.getInventory().getHolder());
    if (active != null) {
      finish(active, event);
      return;
    }
    if (event.getPlayer() instanceof Player player) {
      int removed = purgePlayer(player) + purgeInventory(event.getInventory());
      reportCleanup(player, removed);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onDrop(PlayerDropItemEvent event) {
    if (!isMenuItem(event.getItemDrop().getItemStack())) return;
    event.setCancelled(true);
    event.getItemDrop().remove();
    reportCleanup(event.getPlayer(), 1);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPickup(EntityPickupItemEvent event) {
    if (!isMenuItem(event.getItem().getItemStack())) return;
    event.setCancelled(true);
    event.getItem().remove();
    if (event.getEntity() instanceof Player player) reportCleanup(player, 1);
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    reportCleanup(event.getPlayer(), purgePlayer(event.getPlayer()));
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    UUID id = sessionsByPlayer.get(event.getPlayer().getUniqueId());
    if (id != null && sessions.get(id) != null) finish(sessions.get(id), null);
    purgePlayer(event.getPlayer());
    lastWarning.remove(event.getPlayer().getUniqueId());
  }

  private void handleBottomClick(ActiveMenu active, InventoryClickEvent event) {
    if (isMenuItem(event.getCurrentItem())) {
      int removed = event.getCurrentItem().getAmount();
      event.setCurrentItem(null);
      reportCleanup(active.player, removed);
      return;
    }
    ClickType click = event.getClick();
    if (click.isShiftClick()) {
      ItemStack current = clean(event.getCurrentItem());
      if (isEmpty(current)) return;
      ItemStack leftover = moveIntoStorage(active, current);
      event.setCurrentItem(leftover);
      render(active);
      return;
    }
    if (click == ClickType.DOUBLE_CLICK) {
      collectMatching(active);
      render(active);
      return;
    }
    event.setCancelled(false);
  }

  private void transactStorageClick(
    ActiveMenu active,
    StorageSlot mapping,
    InventoryClickEvent event
  ) {
    ClickType click = event.getClick();
    if (mapping.access() == StorageAccess.TAKE_ONLY) {
      if (click.isShiftClick()) moveStorageToPlayer(active, mapping);
      else if (
        (click == ClickType.LEFT || click == ClickType.RIGHT) &&
        isEmpty(active.player.getItemOnCursor())
      ) cursorTransaction(active, mapping, click == ClickType.RIGHT);
      else if (click == ClickType.DOUBLE_CLICK) collectMatching(active);
      render(active);
      return;
    }
    if (click.isShiftClick()) {
      moveStorageToPlayer(active, mapping);
    } else if (click == ClickType.LEFT || click == ClickType.RIGHT) {
      cursorTransaction(active, mapping, click == ClickType.RIGHT);
    } else if (click == ClickType.NUMBER_KEY) {
      swapHotbar(active, mapping, event.getHotbarButton());
    } else if (click == ClickType.SWAP_OFFHAND) {
      swapOffhand(active, mapping);
    } else if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
      dropStorage(active, mapping, click == ClickType.CONTROL_DROP);
    } else if (click == ClickType.DOUBLE_CLICK) {
      collectMatching(active);
    }
    render(active);
  }

  private void cursorTransaction(
    ActiveMenu active,
    StorageSlot mapping,
    boolean rightClick
  ) {
    ItemStack before = readStorage(active, mapping);
    ItemStack stored = copy(before);
    ItemStack cursor = clean(active.player.getItemOnCursor());
    ItemStack originalCursor = copy(cursor);
    if (!rightClick) {
      if (isEmpty(cursor)) {
        active.player.setItemOnCursor(stored);
        stored = null;
      } else if (isEmpty(stored)) {
        stored = cursor;
        active.player.setItemOnCursor(null);
      } else if (stored.isSimilar(cursor)) {
        int moved = Math.min(
          cursor.getAmount(),
          stored.getMaxStackSize() - stored.getAmount()
        );
        if (moved > 0) {
          stored.setAmount(stored.getAmount() + moved);
          cursor.setAmount(cursor.getAmount() - moved);
          active.player.setItemOnCursor(
            cursor.getAmount() == 0 ? null : cursor
          );
        }
      } else {
        active.player.setItemOnCursor(stored);
        stored = cursor;
      }
    } else if (isEmpty(cursor) && !isEmpty(stored)) {
      int picked = (stored.getAmount() + 1) / 2;
      ItemStack held = stored.clone();
      held.setAmount(picked);
      stored.setAmount(stored.getAmount() - picked);
      active.player.setItemOnCursor(held);
      if (stored.getAmount() == 0) stored = null;
    } else if (!isEmpty(cursor) && isEmpty(stored)) {
      stored = cursor.clone();
      stored.setAmount(1);
      cursor.setAmount(cursor.getAmount() - 1);
      active.player.setItemOnCursor(cursor.getAmount() == 0 ? null : cursor);
    } else if (
      !isEmpty(cursor) &&
      stored.isSimilar(cursor) &&
      stored.getAmount() < stored.getMaxStackSize()
    ) {
      stored.setAmount(stored.getAmount() + 1);
      cursor.setAmount(cursor.getAmount() - 1);
      active.player.setItemOnCursor(cursor.getAmount() == 0 ? null : cursor);
    }
    if (!setStorage(active, mapping, before, stored)) {
      active.player.setItemOnCursor(originalCursor);
    }
  }

  private void moveStorageToPlayer(ActiveMenu active, StorageSlot mapping) {
    ItemStack before = readStorage(active, mapping);
    if (isEmpty(before)) return;
    ItemStack[] playerBefore = cloneContents(
      active.player.getInventory().getStorageContents()
    );
    Map<Integer, ItemStack> leftovers = active.player
      .getInventory()
      .addItem(before.clone());
    ItemStack remaining = leftovers.values().stream().findFirst().orElse(null);
    if (!setStorage(active, mapping, before, remaining)) {
      active.player.getInventory().setStorageContents(playerBefore);
    }
  }

  private ItemStack moveIntoStorage(ActiveMenu active, ItemStack input) {
    ItemStack remaining = input.clone();
    for (StorageSlot mapping : orderedWritableStorage(active)) {
      ItemStack stored = readStorage(active, mapping);
      if (
        isEmpty(stored) ||
        !stored.isSimilar(remaining) ||
        stored.getAmount() >= stored.getMaxStackSize()
      ) continue;
      ItemStack before = stored.clone();
      int moved = Math.min(
        remaining.getAmount(),
        stored.getMaxStackSize() - stored.getAmount()
      );
      stored.setAmount(stored.getAmount() + moved);
      if (setStorage(active, mapping, before, stored)) {
        remaining.setAmount(remaining.getAmount() - moved);
        if (remaining.getAmount() == 0) return null;
      }
    }
    for (StorageSlot mapping : orderedWritableStorage(active)) {
      if (!isEmpty(readStorage(active, mapping))) continue;
      int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
      ItemStack placed = remaining.clone();
      placed.setAmount(moved);
      if (setStorage(active, mapping, null, placed)) {
        remaining.setAmount(remaining.getAmount() - moved);
        if (remaining.getAmount() == 0) return null;
      }
    }
    return remaining;
  }

  private void swapHotbar(
    ActiveMenu active,
    StorageSlot mapping,
    int hotbarSlot
  ) {
    if (hotbarSlot < 0 || hotbarSlot > 8) return;
    ItemStack before = readStorage(active, mapping);
    ItemStack hotbar = clean(active.player.getInventory().getItem(hotbarSlot));
    active.player.getInventory().setItem(hotbarSlot, before);
    if (!setStorage(active, mapping, before, hotbar)) {
      active.player.getInventory().setItem(hotbarSlot, hotbar);
    }
  }

  private void swapOffhand(ActiveMenu active, StorageSlot mapping) {
    ItemStack before = readStorage(active, mapping);
    ItemStack offhand = clean(active.player.getInventory().getItemInOffHand());
    active.player
      .getInventory()
      .setItemInOffHand(before == null ? new ItemStack(Material.AIR) : before);
    if (!setStorage(active, mapping, before, offhand)) {
      active.player
        .getInventory()
        .setItemInOffHand(
          offhand == null ? new ItemStack(Material.AIR) : offhand
        );
    }
  }

  private void dropStorage(
    ActiveMenu active,
    StorageSlot mapping,
    boolean all
  ) {
    ItemStack before = readStorage(active, mapping);
    if (isEmpty(before)) return;
    ItemStack dropped = before.clone();
    dropped.setAmount(all ? before.getAmount() : 1);
    ItemStack remaining = before.clone();
    remaining.setAmount(before.getAmount() - dropped.getAmount());
    if (
      !setStorage(
        active,
        mapping,
        before,
        remaining.getAmount() == 0 ? null : remaining
      )
    ) return;
    Item entity = active.player
      .getWorld()
      .dropItemNaturally(active.player.getLocation(), dropped);
    entity.setPickupDelay(20);
  }

  private void collectMatching(ActiveMenu active) {
    ItemStack cursor = clean(active.player.getItemOnCursor());
    if (isEmpty(cursor)) return;
    int needed = cursor.getMaxStackSize() - cursor.getAmount();
    if (needed <= 0) return;
    for (StorageSlot mapping : orderedWritableStorage(active)) {
      ItemStack stored = readStorage(active, mapping);
      if (isEmpty(stored) || !stored.isSimilar(cursor)) continue;
      ItemStack before = stored.clone();
      int moved = Math.min(needed, stored.getAmount());
      stored.setAmount(stored.getAmount() - moved);
      if (
        setStorage(
          active,
          mapping,
          before,
          stored.getAmount() == 0 ? null : stored
        )
      ) {
        cursor.setAmount(cursor.getAmount() + moved);
        needed -= moved;
        if (needed == 0) break;
      }
    }
    for (
      int slot = 0;
      needed > 0 && slot < active.player.getInventory().getSize();
      slot++
    ) {
      ItemStack item = clean(active.player.getInventory().getItem(slot));
      if (isEmpty(item) || !item.isSimilar(cursor)) continue;
      int moved = Math.min(needed, item.getAmount());
      cursor.setAmount(cursor.getAmount() + moved);
      item.setAmount(item.getAmount() - moved);
      active.player
        .getInventory()
        .setItem(slot, item.getAmount() == 0 ? null : item);
      needed -= moved;
    }
    active.player.setItemOnCursor(cursor);
  }

  private boolean setStorage(
    ActiveMenu active,
    StorageSlot mapping,
    ItemStack before,
    ItemStack value
  ) {
    ItemStack cleanValue = clean(value);
    if (!writeStorage(active, mapping, cleanValue)) return false;
    storageChanged(active, mapping, before, cleanValue);
    return true;
  }

  private void storageChanged(
    ActiveMenu active,
    StorageSlot mapping,
    ItemStack before,
    ItemStack value
  ) {
    invoke(
      active,
      "storage callback at menu slot " +
      mapping.menuSlot() +
      " / provider index " +
      mapping.storageSlot(),
      active.template.storageChangeAction(),
      new MenuStorageChangeContext(
        active.player,
        active.session,
        mapping.menuSlot(),
        mapping.storageSlot(),
        active.storage,
        before,
        value
      )
    );
  }

  private List<StorageSlot> orderedWritableStorage(ActiveMenu active) {
    return active.rendered
      .storageSlots()
      .values()
      .stream()
      .filter(slot -> slot.access() == StorageAccess.READ_WRITE)
      .sorted(Comparator.comparingInt(StorageSlot::menuSlot))
      .toList();
  }

  private void render(ActiveMenu active) {
    active.inventory.clear();
    active.liveItems.clear();
    active.liveItems.putAll(active.rendered.items());
    for (Map.Entry<Integer, MenuItem> entry : active.liveItems.entrySet()) {
      active.inventory.setItem(
        entry.getKey(),
        tag(entry.getValue().render(active.player), active.session.sessionId())
      );
    }
    if (active.storage != null) {
      for (StorageSlot mapping : active.rendered.storageSlots().values()) {
        ItemStack stored = readStorage(active, mapping);
        if (!isEmpty(stored)) active.inventory.setItem(
          mapping.menuSlot(),
          tag(stored, active.session.sessionId())
        );
      }
    }
  }

  private ItemStack readStorage(ActiveMenu active, StorageSlot mapping) {
    try {
      ItemStack item = clean(
        active.storage.getItem(mapping.storageSlot(), active.player)
      );
      active.failedStorageReads.remove(mapping.storageSlot());
      return item;
    } catch (Throwable exception) {
      active.failedStorageReads.add(mapping.storageSlot());
      providerFailure(active, "read", mapping, exception);
      return null;
    }
  }

  private boolean writeStorage(
    ActiveMenu active,
    StorageSlot mapping,
    ItemStack value
  ) {
    if (active.failedStorageReads.contains(mapping.storageSlot())) return false;
    try {
      active.storage.setItem(mapping.storageSlot(), clean(value));
      return true;
    } catch (Throwable exception) {
      providerFailure(active, "write", mapping, exception);
      return false;
    }
  }

  private void providerFailure(
    ActiveMenu active,
    String operation,
    StorageSlot mapping,
    Throwable exception
  ) {
    plugin
      .getLogger()
      .log(
        Level.SEVERE,
        "StorageProvider " +
        active.storage.getClass().getName() +
        " failed to " +
        operation +
        " index " +
        mapping.storageSlot() +
        " for menu slot " +
        mapping.menuSlot() +
        ", player " +
        active.player.getName() +
        ", session " +
        active.session.sessionId(),
        exception
      );
  }

  private ItemStack tag(ItemStack source, UUID sessionId) {
    ItemStack copy = clean(source);
    var meta = copy.getItemMeta();
    meta
      .getPersistentDataContainer()
      .set(menuItemKey, PersistentDataType.STRING, MENU_LABEL);
    meta
      .getPersistentDataContainer()
      .set(menuSessionKey, PersistentDataType.STRING, sessionId.toString());
    copy.setItemMeta(meta);
    return copy;
  }

  private ItemStack clean(ItemStack source) {
    if (isEmpty(source)) return null;
    ItemStack copy = source.clone();
    if (copy.hasItemMeta()) {
      var meta = copy.getItemMeta();
      meta.getPersistentDataContainer().remove(menuItemKey);
      meta.getPersistentDataContainer().remove(menuSessionKey);
      copy.setItemMeta(meta);
    }
    return copy;
  }

  private int purgePlayer(Player player) {
    int removed = purgeInventory(player.getInventory());
    ItemStack cursor = player.getItemOnCursor();
    if (isMenuItem(cursor)) {
      removed += cursor.getAmount();
      player.setItemOnCursor(null);
    }
    return removed;
  }

  private int purgeInventory(Inventory inventory) {
    int removed = 0;
    for (int slot = 0; slot < inventory.getSize(); slot++) {
      ItemStack item = inventory.getItem(slot);
      if (!isMenuItem(item)) continue;
      removed += item.getAmount();
      inventory.clear(slot);
    }
    return removed;
  }

  private void periodicCleanup() {
    for (Player player : Bukkit.getOnlinePlayers())
      reportCleanup(player, purgePlayer(player));
  }

  private void reportCleanup(Player player, int amount) {
    if (amount <= 0) return;
    long now = System.currentTimeMillis();
    long last = lastWarning.getOrDefault(player.getUniqueId(), 0L);
    if (now - last < WARNING_INTERVAL_MILLIS) return;
    lastWarning.put(player.getUniqueId(), now);
    //    try {
    //      player.sendMessage(Locale.get(player, "menu.duplicate_removed", amount));
    //    } catch (RuntimeException unavailable) {
    //      player.sendMessage("§cRemoved " + amount + " invalid menu item(s).");
    //    }
  }

  private void finish(ActiveMenu active, InventoryCloseEvent event) {
    if (sessions.remove(active.session.sessionId()) == null) return;
    sessionsByPlayer.remove(
      active.player.getUniqueId(),
      active.session.sessionId()
    );
    reportCleanup(active.player, purgePlayer(active.player));
    invoke(
      active,
      "close callback",
      active.template.closeAction(),
      new MenuCloseContext(active.player, active.session, event)
    );
    if (
      event == null &&
      active.player.isOnline() &&
      active.player.getOpenInventory().getTopInventory() == active.inventory
    ) {
      active.player.closeInventory();
    }
  }

  private void invokeUnhandled(
    ActiveMenu active,
    int menuSlot,
    Integer storageSlot,
    StorageProvider storageProvider,
    InventoryClickEvent event
  ) {
    invoke(
      active,
      "unhandled click callback at slot " + menuSlot,
      active.template.unhandledClickAction(),
      new MenuClickContext(
        active.player,
        active.session,
        menuSlot,
        storageSlot,
        storageProvider,
        event
      )
    );
  }

  private <T> void invoke(
    ActiveMenu active,
    String label,
    Consumer<T> callback,
    T context
  ) {
    if (callback == null) return;
    try {
      callback.accept(context);
    } catch (Throwable exception) {
      plugin
        .getLogger()
        .log(
          Level.SEVERE,
          "Menu " +
          label +
          " failed for " +
          active.player.getName() +
          " in session " +
          active.session.sessionId(),
          exception
        );
    }
  }

  private ActiveMenu requireActive(MenuSession session) {
    requirePrimaryThread();
    Objects.requireNonNull(session, "session");
    ActiveMenu active = sessions.get(session.sessionId());
    if (
      active == null || active.session != session
    ) throw new IllegalStateException("Menu session is no longer active");
    return active;
  }

  private ActiveMenu active(InventoryHolder holder) {
    if (!(holder instanceof BundlerMenuHolder menuHolder)) return null;
    return sessions.get(menuHolder.sessionId());
  }

  private void validateStorage(
    Map<Integer, StorageSlot> mappings,
    StorageProvider storage
  ) {
    int size = storage.size();
    if (size < 0) throw new IllegalArgumentException(
      "StorageProvider size cannot be negative"
    );
    for (StorageSlot mapping : mappings.values()) {
      if (!storage.containsIndex(mapping.storageSlot())) {
        throw new IllegalArgumentException(
          "StorageProvider " +
          storage.getClass().getSimpleName() +
          " has no index " +
          mapping.storageSlot()
        );
      }
    }
  }

  private static void validateMenuSlot(MenuTemplate template, int slot) {
    if (
      slot < 0 || slot >= template.rows() * 9
    ) throw new IllegalArgumentException("Invalid menu slot " + slot);
  }

  private static boolean isEmpty(ItemStack item) {
    return item == null || item.getType().isAir() || item.getAmount() <= 0;
  }

  static boolean isInvalidShiftPlacement(
    boolean shiftClick,
    ItemStack current,
    ItemStack cursor
  ) {
    return shiftClick && isEmpty(current) && !isEmpty(cursor);
  }

  private static ItemStack copy(ItemStack item) {
    return isEmpty(item) ? null : item.clone();
  }

  private static ItemStack[] cloneContents(ItemStack[] contents) {
    ItemStack[] copied = new ItemStack[contents.length];
    for (int index = 0; index < contents.length; index++) copied[index] = copy(
      contents[index]
    );
    return copied;
  }

  private static void requirePrimaryThread() {
    if (!Bukkit.isPrimaryThread()) throw new IllegalStateException(
      "Menu inventory operations must run on the server thread"
    );
  }

  private static final class ActiveMenu {

    private final MenuSession session;
    private final Player player;
    private final MenuTemplate template;
    private final StorageProvider storage;
    private final Inventory inventory;
    private final Map<Integer, MenuItem> liveItems = new LinkedHashMap<>();
    private final Set<Integer> failedStorageReads = new HashSet<>();
    private RenderedMenuPage rendered;

    private ActiveMenu(
      MenuSession session,
      Player player,
      MenuTemplate template,
      StorageProvider storage,
      Inventory inventory,
      RenderedMenuPage rendered
    ) {
      this.session = session;
      this.player = player;
      this.template = template;
      this.storage = storage;
      this.inventory = inventory;
      this.rendered = rendered;
    }
  }
}
