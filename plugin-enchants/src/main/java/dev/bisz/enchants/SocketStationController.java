package dev.bisz.enchants;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.chat.ChatUtils;
import dev.bisz.items.DevEnchantment;
import dev.bisz.items.DevItemStack;
import dev.bisz.items.EnchantmentData;
import dev.bisz.items.ItemsPlugin;
import dev.bisz.items.RomanNumerals;
import dev.bisz.menus.MenuItem;
import dev.bisz.menus.MenuSession;
import dev.bisz.menus.SinglePageMenuTemplate;
import dev.bisz.menus.StorageProvider;
import dev.bisz.players.locales.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * trueMC's anvil and grindstone stations. Right-clicking either block opens the
 * trueMC socket menu, which mirrors the enchanting table: the item rests in a
 * storage cell, empty sockets render as category dyes, and filled sockets
 * render as ender eyes. The anvil turns an enchanted book placed on an empty
 * socket into that socket's enchantment, while the grindstone strips a clicked
 * socket and refunds experience.
 */
final class SocketStationController implements Listener {
  private static final int ICON_SLOT = 4;
  private static final int ITEM_SLOT = 22;
  private static final int REPAIR_SLOT = 48;
  private static final int CLOSE_SLOT = 49;
  private static final int INFO_SLOT = 50;
  private static final int SOCKET_STORAGE_BASE = 1;
  private static final int SOCKET_STORAGE_SIZE = SocketMenuVisuals.SOCKET_SLOTS.length;

  private final EnchantsPlugin plugin;
  private final Map<UUID, Station> stations = new LinkedHashMap<>();

  SocketStationController(EnchantsPlugin plugin) {
    this.plugin = plugin;
  }

  void dispose() {
    new ArrayList<>(stations.values()).forEach(station -> {
      if (station.session != null) station.session.close();
    });
    stations.clear();
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
    Block block = event.getClickedBlock();
    if (block == null) return;
    StationType type = StationType.of(block.getType());
    if (type == null) return;
    event.setCancelled(true);
    open(event.getPlayer(), type);
  }

  private void open(Player player, StationType type) {
    Station station = new Station(player, type);
    SinglePageMenuTemplate.Builder builder = SinglePageMenuTemplate.builder(
      viewer -> Locale.get(viewer, type.titleKey()), 6);
    for (int slot = 0; slot < 54; slot++) builder.item(slot, filler());
    for (int slot : SocketMenuVisuals.BLACK_SLOTS) builder.item(slot, filler(Material.BLACK_STAINED_GLASS_PANE));
    builder.item(ICON_SLOT, MenuItem.builder(type.icon())
      .localizedName(type.nameKey())
      .wrappedLocalizedLore(type.descriptionKey())
      .build());
    builder.storageIndex(ITEM_SLOT, 0);
    builder.removeItem(ITEM_SLOT);
    if (type == StationType.ANVIL) {
      builder.item(REPAIR_SLOT, MenuItem.builder(Material.ANVIL)
        .localizedName("enchants.station.repair.name")
        .wrappedLocalizedLore("enchants.station.repair.description")
        .lore(viewer -> repairCostLore(station, viewer))
        .onClick(context -> {
          repair(station);
          context.session().refresh();
        })
        .build());
    }
    builder.item(INFO_SLOT, MenuItem.builder(Material.KNOWLEDGE_BOOK)
      .localizedName("enchants.station.info.name")
      .wrappedLocalizedLore(type.infoKey())
      .build());
    builder.item(CLOSE_SLOT, MenuItem.builder(Material.BARRIER)
      .localizedName("locale.menu.close")
      .onClick(context -> context.session().close())
      .build());
    for (int position = 0; position < SOCKET_STORAGE_SIZE; position++) {
      int gridSlot = SocketMenuVisuals.SOCKET_SLOTS[position];
      if (type == StationType.ANVIL) {
        builder.storageIndex(gridSlot, SOCKET_STORAGE_BASE + position);
        builder.item(gridSlot, MenuItem.dynamic(viewer -> renderAnvilSocket(station, gridSlot, viewer)));
      } else {
        builder.item(gridSlot, MenuItem.dynamic(viewer -> renderGrindstoneSocket(station, gridSlot, viewer))
          .onClick(context -> {
            strip(station, gridSlot);
            context.session().refresh();
          }));
      }
    }
    builder.onStorageChange(context -> storageChanged(station, context.storageIndex(), context.item()));
    builder.onClose(context -> finish(station));
    station.session = BundlerPlugin.instance().menuManager().open(player, builder.build(), station.storage);
    stations.put(player.getUniqueId(), station);
    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, .6F, type == StationType.ANVIL ? 1F : .7F);
  }

  private void storageChanged(Station station, int storageIndex, ItemStack placed) {
    if (station.finished) return;
    if (storageIndex == 0) {
      itemChanged(station);
      return;
    }
    int position = storageIndex - SOCKET_STORAGE_BASE;
    if (position < 0 || position >= SOCKET_STORAGE_SIZE) return;
    int gridSlot = SocketMenuVisuals.SOCKET_SLOTS[position];
    station.storage.setItem(storageIndex, null);
    if (station.type != StationType.ANVIL) {
      giveOrDrop(station.player, placed);
      return;
    }
    applyBook(station, gridSlot, placed);
  }

  private void itemChanged(Station station) {
    ItemStack item = station.storage.item();
    if (item != null && item.getAmount() > 1) {
      ItemStack surplus = item.clone();
      surplus.setAmount(item.getAmount() - 1);
      item.setAmount(1);
      station.storage.setItem(0, item);
      giveOrDrop(station.player, surplus);
    }
    ItemStack stored = station.storage.item();
    if (stored != null && !SocketedVanillaItem.supports(stored.getType())) {
      giveOrDrop(station.player, stored);
      station.storage.setItem(0, null);
      station.player.sendMessage(Locale.get(station.player, "enchants.station.item.invalid"));
    }
    station.storage.takeSockets().forEach(leftover -> giveOrDrop(station.player, leftover));
  }

  private void applyBook(Station station, int gridSlot, ItemStack placed) {
    if (placed == null || placed.getType() != Material.ENCHANTED_BOOK) {
      giveOrDrop(station.player, placed);
      return;
    }
    ItemStack item = station.storage.item();
    if (item == null) {
      giveOrDrop(station.player, placed);
      station.player.sendMessage(Locale.get(station.player, "enchants.station.need_item"));
      return;
    }
    ItemStack target = item;
    if (target.getType() == Material.BOOK) {
      target = target.clone();
      target.setType(Material.ENCHANTED_BOOK);
    }
    DevItemStack stack = wrap(target);
    if (stack == null || !(stack.definition() instanceof SocketedVanillaItem definition)) {
      giveOrDrop(station.player, placed);
      station.player.sendMessage(Locale.get(station.player, "enchants.station.need_item"));
      return;
    }
    Integer socket = SocketMenuVisuals.socketAt(target, gridSlot);
    if (socket == null) {
      giveOrDrop(station.player, placed);
      return;
    }
    Map<Integer, Map.Entry<DevEnchantment, EnchantmentData>> assigned = definition.normalize(stack);
    if (assigned.containsKey(socket)) {
      giveOrDrop(station.player, placed);
      reject(station, "enchants.station.socket.occupied");
      return;
    }
    EnchantmentSlot slot = definition.sockets().get(socket);
    int cost = plugin.enchantingCosts().materialCost(target.getType());
    if (station.player.getLevel() < cost) {
      giveOrDrop(station.player, placed);
      reject(station, "enchants.station.need_levels", cost);
      return;
    }
    DevItemStack book = wrap(placed);
    if (book == null) {
      giveOrDrop(station.player, placed);
      return;
    }
    for (Map.Entry<DevEnchantment, EnchantmentData> entry : book.enchantmentData().entrySet()) {
      DevEnchantment enchantment = entry.getKey();
      if (!slot.accepts(enchantment, target.getType())) continue;
      Map<NamespacedKey, Object> metadata = new HashMap<>(entry.getValue().metadata());
      metadata.put(SocketedVanillaItem.slotKey(), socket);
      metadata.put(SocketedVanillaItem.categoryKey(), slot.category().ordinal());
      try {
        definition.applySocketEnchantment(stack, enchantment,
          new EnchantmentData(entry.getValue().level(), metadata));
      } catch (RuntimeException exception) {
        continue;
      }
      if (placed.getAmount() > 1) {
        ItemStack surplus = placed.clone();
        surplus.setAmount(placed.getAmount() - 1);
        giveOrDrop(station.player, surplus);
      }
      LinearExperience.removeLevels(station.player, cost);
      stack.render(station.player);
      station.storage.setItem(0, stack.bukkitStack());
      station.player.playSound(station.player.getLocation(), Sound.BLOCK_ANVIL_USE, .8F, 1.2F);
      station.player.sendMessage(Locale.get(station.player, "enchants.station.socket.applied",
        enchantment.displayName(station.player), RomanNumerals.format(entry.getValue().level())));
      return;
    }
    giveOrDrop(station.player, placed);
    reject(station, "enchants.station.socket.no_match");
  }

  private void strip(Station station, int gridSlot) {
    ItemStack item = station.storage.item();
    DevItemStack stack = wrap(item);
    if (stack == null || !(stack.definition() instanceof SocketedVanillaItem definition)) return;
    Integer socket = SocketMenuVisuals.socketAt(item, gridSlot);
    if (socket == null) return;
    if (!definition.normalize(stack).containsKey(socket)) {
      station.player.sendMessage(Locale.get(station.player, "enchants.station.socket.nothing_to_strip"));
      return;
    }
    definition.removeSocket(stack, socket);
    stack.render(station.player);
    station.storage.setItem(0, stack.bukkitStack());
    int refund = plugin.enchantingCosts().refund(item.getType());
    if (refund > 0) LinearExperience.addPoints(station.player, refund * LinearExperience.pointsPerLevel());
    station.player.playSound(station.player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, .8F, 1F);
  }

  private void repair(Station station) {
    ItemStack item = station.storage.item();
    if (item == null) {
      station.player.sendMessage(Locale.get(station.player, "enchants.station.need_item"));
      return;
    }
    ItemMeta meta = item.getItemMeta();
    if (!(meta instanceof Damageable damageable)) {
      station.player.sendMessage(Locale.get(station.player, "enchants.station.repair.cannot"));
      return;
    }
    if (damageable.getDamage() <= 0) {
      station.player.sendMessage(Locale.get(station.player, "enchants.station.repair.full"));
      return;
    }
    RepairCost cost = repairCost(item);
    if (cost == null) {
      station.player.sendMessage(Locale.get(station.player, "enchants.station.repair.cannot"));
      return;
    }
    if (!cost.free()) {
      if (!station.player.getInventory().containsAtLeast(new ItemStack(cost.material()), 1)) {
        station.player.sendMessage(Locale.get(station.player, "enchants.station.repair.material",
          countedMaterial(station.player, cost.material())));
        return;
      }
      if (station.player.getLevel() < cost.levels()) {
        station.player.sendMessage(Locale.get(station.player, "enchants.station.need_levels", cost.levels()));
        return;
      }
      station.player.getInventory().removeItem(new ItemStack(cost.material(), 1));
      if (cost.levels() > 0) LinearExperience.removeLevels(station.player, cost.levels());
    }
    damageable.setDamage(0);
    item.setItemMeta(meta);
    DevItemStack stack = wrap(item);
    if (stack != null) {
      stack.render(station.player);
      item = stack.bukkitStack();
    }
    station.storage.setItem(0, item);
    station.player.playSound(station.player.getLocation(), Sound.BLOCK_ANVIL_USE, .8F, 1.4F);
  }

  /** Cost lore shown on the Repair button before anything is consumed. */
  private List<String> repairCostLore(Station station, Player viewer) {
    ItemStack item = station.storage.item();
    if (item == null) return List.of();
    ItemMeta meta = item.getItemMeta();
    if (!(meta instanceof Damageable damageable) || damageable.getDamage() <= 0) return List.of();
    RepairCost cost = repairCost(item);
    if (cost == null) return List.of("", Locale.get(viewer, "enchants.station.repair.cannot"));
    if (cost.free()) return List.of("", Locale.get(viewer, "enchants.station.repair.free"));
    ArrayList<String> lore = new ArrayList<>();
    lore.add("");
    lore.add(countedMaterial(viewer, cost.material()));
    if (cost.levels() > 0) lore.add(Locale.get(viewer, "enchants.station.repair.levels", cost.levels()));
    return lore;
  }

  /** Material and level cost of repairing a damaged item, or null when it cannot be repaired. */
  private RepairCost repairCost(ItemStack item) {
    DevItemStack stack = wrap(item);
    boolean mending = stack != null
      && stack.enchantmentData().keySet().stream().anyMatch(SocketedVanillaItem::isMending);
    if (mending) return new RepairCost(null, 0);
    Material material = repairMaterial(item.getType());
    if (material == null) return null;
    return new RepairCost(material, plugin.enchantingCosts().materialCost(item.getType()));
  }

  private static String countedMaterial(Player viewer, Material material) {
    return ChatUtils.countedItem(1, itemDisplayName(viewer, new ItemStack(material)));
  }

  /** Rendered item name including its quality color and any custom name override. */
  private static String itemDisplayName(Player viewer, ItemStack item) {
    ItemStack copy = item.clone();
    try {
      DevItemStack rendered = ItemsPlugin.instance().factory().refresh(copy, viewer);
      ItemMeta meta = rendered.bukkitStack().getItemMeta();
      if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
    } catch (RuntimeException ignored) {}
    return "§f" + copy.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
  }

  private ItemStack renderAnvilSocket(Station station, int gridSlot, Player viewer) {
    ItemStack item = station.storage.item();
    Integer socket = SocketMenuVisuals.socketAt(item, gridSlot);
    if (socket == null) return SocketMenuVisuals.placeholderDye();
    try {
      DevItemStack wrapped = ItemsPlugin.instance().factory().wrap(item);
      if (!(wrapped.definition() instanceof SocketedVanillaItem definition))
        return SocketMenuVisuals.placeholderDye();
      EnchantmentSlot slot = definition.sockets().get(socket);
      Map.Entry<DevEnchantment, EnchantmentData> filled = definition.normalize(wrapped).get(socket);
      if (filled != null) return SocketMenuVisuals.filledSocket(slot, filled, item.getType(), viewer,
        List.of(Locale.get(viewer, "enchants.station.socket.occupied")));
      int cost = plugin.enchantingCosts().materialCost(item.getType());
      ArrayList<String> hint = new ArrayList<>();
      hint.add("");
      hint.add(Locale.get(viewer, "enchants.station.socket.place_book"));
      if (cost > 0) hint.add(Locale.get(viewer, "enchants.station.socket.cost", cost));
      return SocketMenuVisuals.emptySocket(slot, viewer, hint);
    } catch (RuntimeException ignored) {
      return SocketMenuVisuals.placeholderDye();
    }
  }

  private ItemStack renderGrindstoneSocket(Station station, int gridSlot, Player viewer) {
    ItemStack item = station.storage.item();
    Integer socket = SocketMenuVisuals.socketAt(item, gridSlot);
    if (socket == null) return SocketMenuVisuals.placeholderDye();
    try {
      DevItemStack wrapped = ItemsPlugin.instance().factory().wrap(item);
      if (!(wrapped.definition() instanceof SocketedVanillaItem definition))
        return SocketMenuVisuals.placeholderDye();
      EnchantmentSlot slot = definition.sockets().get(socket);
      Map.Entry<DevEnchantment, EnchantmentData> filled = definition.normalize(wrapped).get(socket);
      if (filled == null) return SocketMenuVisuals.emptySocket(slot, viewer, List.of(
        "", Locale.get(viewer, "enchants.station.socket.nothing_to_strip")));
      int refund = plugin.enchantingCosts().refund(item.getType());
      if (refund > 0) return SocketMenuVisuals.filledSocket(slot, filled, item.getType(), viewer, List.of(
        Locale.get(viewer, "enchants.station.socket.strip.action"),
        Locale.get(viewer, "enchants.station.socket.strip.cost", refund)));
      return SocketMenuVisuals.filledSocket(slot, filled, item.getType(), viewer, List.of(
        Locale.get(viewer, "enchants.station.socket.strip.no_refund")));
    } catch (RuntimeException ignored) {
      return SocketMenuVisuals.placeholderDye();
    }
  }

  private static void reject(Station station, String key, Object... arguments) {
    station.player.sendMessage(Locale.get(station.player, key, arguments));
    station.player.playSound(station.player.getLocation(), Sound.ENTITY_VILLAGER_NO, .8F, 1F);
  }

  private static DevItemStack wrap(ItemStack item) {
    if (item == null) return null;
    try {
      return ItemsPlugin.instance().factory().wrap(item);
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private void finish(Station station) {
    if (station.finished) return;
    station.finished = true;
    stations.remove(station.player.getUniqueId(), station);
    giveOrDrop(station.player, station.storage.takeItem());
    station.storage.takeSockets().forEach(leftover -> giveOrDrop(station.player, leftover));
  }

  private static void giveOrDrop(Player player, ItemStack item) {
    if (item == null || item.getType().isAir() || item.getAmount() <= 0) return;
    player.getInventory().addItem(item).values().forEach(leftover ->
      player.getWorld().dropItemNaturally(player.getLocation(), leftover));
  }

  private static MenuItem filler() {
    return filler(Material.GRAY_STAINED_GLASS_PANE);
  }

  private static MenuItem filler(Material material) {
    return MenuItem.builder(material).localizedName("enchants.menu.blank").build();
  }

  private static Material repairMaterial(Material material) {
    String name = material.name();
    if (name.startsWith("WOODEN_")) return Material.OAK_PLANKS;
    if (name.startsWith("STONE_")) return Material.COBBLESTONE;
    if (name.startsWith("COPPER_")) return Material.COPPER_INGOT;
    if (name.startsWith("GOLDEN_")) return Material.GOLD_INGOT;
    if (name.startsWith("IRON_")) return Material.IRON_INGOT;
    if (name.startsWith("DIAMOND_")) return Material.DIAMOND;
    if (name.startsWith("NETHERITE_")) return Material.NETHERITE_INGOT;
    if (name.startsWith("LEATHER_")) return Material.LEATHER;
    if (name.startsWith("CHAINMAIL_")) return Material.IRON_INGOT;
    if (name.equals("TURTLE_HELMET")) return Material.TURTLE_SCUTE;
    return null;
  }

  private enum StationType {
    ANVIL("enchants.station.anvil.title", "enchants.station.anvil.name",
      "enchants.station.anvil.description", "enchants.station.info.anvil", Material.ANVIL),
    GRINDSTONE("enchants.station.grindstone.title", "enchants.station.grindstone.name",
      "enchants.station.grindstone.description", "enchants.station.info.grindstone", Material.GRINDSTONE);

    private final String titleKey;
    private final String nameKey;
    private final String descriptionKey;
    private final String infoKey;
    private final Material icon;

    StationType(String titleKey, String nameKey, String descriptionKey, String infoKey, Material icon) {
      this.titleKey = titleKey;
      this.nameKey = nameKey;
      this.descriptionKey = descriptionKey;
      this.infoKey = infoKey;
      this.icon = icon;
    }

    private String titleKey() { return titleKey; }
    private String nameKey() { return nameKey; }
    private String descriptionKey() { return descriptionKey; }
    private String infoKey() { return infoKey; }
    private Material icon() { return icon; }

    static StationType of(Material material) {
      return switch (material) {
        case ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL -> ANVIL;
        case GRINDSTONE -> GRINDSTONE;
        default -> null;
      };
    }
  }

  /** Resolved repair price; a null material means the Mending repair is free. */
  private record RepairCost(Material material, int levels) {
    private boolean free() { return material == null; }
  }

  private static final class Station {
    private final Player player;
    private final StationType type;
    private final StationStorage storage = new StationStorage();
    private MenuSession session;
    private boolean finished;

    private Station(Player player, StationType type) {
      this.player = player;
      this.type = type;
    }
  }

  /** Index 0 holds the target item; indices 1-10 hold socket book placements. */
  private static final class StationStorage extends StorageProvider {
    private ItemStack item;
    private final ItemStack[] sockets = new ItemStack[SOCKET_STORAGE_SIZE];

    @Override public int size() { return 1 + SOCKET_STORAGE_SIZE; }

    @Override public ItemStack getItem(int index) {
      requireIndex(index);
      ItemStack value = index == 0 ? item : sockets[index - 1];
      return value == null ? null : value.clone();
    }

    @Override public void setItem(int index, ItemStack value) {
      requireIndex(index);
      ItemStack stored = value == null || value.getType().isAir() ? null : value.clone();
      if (index == 0) item = stored;
      else sockets[index - 1] = stored;
    }

    ItemStack item() {
      return item == null ? null : item.clone();
    }

    ItemStack takeItem() {
      ItemStack result = item;
      item = null;
      return result;
    }

    List<ItemStack> takeSockets() {
      ArrayList<ItemStack> leftover = new ArrayList<>(sockets.length);
      for (int index = 0; index < sockets.length; index++) {
        if (sockets[index] != null) leftover.add(sockets[index]);
        sockets[index] = null;
      }
      return leftover;
    }

    private static void requireIndex(int index) {
      if (index < 0 || index >= 1 + SOCKET_STORAGE_SIZE) throw new IndexOutOfBoundsException(index);
    }
  }
}
