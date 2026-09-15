package dev.bisz.enchants;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.items.DevEnchantment;
import dev.bisz.items.DevItemStack;
import dev.bisz.items.EnchantmentData;
import dev.bisz.items.EnchantmentDisplay;
import dev.bisz.items.ItemsPlugin;
import dev.bisz.items.RomanNumerals;
import dev.bisz.menus.MenuItem;
import dev.bisz.menus.MenuSession;
import dev.bisz.menus.SinglePageMenuTemplate;
import dev.bisz.menus.StorageProvider;
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
 * trueMC's anvil and grindstone stations. Right-clicking either block while
 * holding a socketable item opens a socket menu: the anvil applies enchanted
 * books (and repairs), while the grindstone strips a single chosen socket.
 */
final class SocketStationController implements Listener {
  private static final int ITEM_SLOT = 4;
  private static final int CLOSE_SLOT = 49;
  private static final int REPAIR_SLOT = 48;

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
    ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
    if (!socketable(held)) return;
    event.setCancelled(true);
    open(event.getPlayer(), type, held.clone());
  }

  private void open(Player player, StationType type, ItemStack item) {
    Station station = new Station(player, type, item);
    SinglePageMenuTemplate.Builder builder = SinglePageMenuTemplate.builder(
      viewer -> type.title, 6);
    for (int slot = 0; slot < 54; slot++) builder.item(slot, filler());
    builder.storageIndex(ITEM_SLOT, 0);
    builder.item(ITEM_SLOT, MenuItem.dynamic(viewer -> {
      ItemStack stored = station.storage.getItem(0);
      return stored == null ? grayPane() : stored;
    }).onClick(context -> {}));
    if (type == StationType.ANVIL) {
      builder.item(REPAIR_SLOT, MenuItem.builder(Material.ANVIL)
        .name("§eRepair")
        .lore("§7Restore the item to full durability.", "§7Mending items repair for free.")
        .onClick(context -> {
          repair(station);
          context.session().refresh();
        })
        .build());
    }
    builder.item(CLOSE_SLOT, MenuItem.builder(Material.BARRIER)
      .localizedName("locale.menu.close")
      .onClick(context -> context.session().close())
      .build());
    for (int index = 0; index < station.sockets(); index++) {
      int socket = index;
      int gridSlot = EnchantingMenuController.gridPosition(index, station.sockets());
      builder.item(gridSlot, MenuItem.dynamic(viewer -> renderSocket(station, socket, viewer))
        .onClick(context -> {
          click(station, socket);
          context.session().refresh();
        }));
    }
    builder.onClose(context -> finish(station));
    station.session = BundlerPlugin.instance().menuManager().open(player, builder.build(), station.storage);
    stations.put(player.getUniqueId(), station);
    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, .6F, type == StationType.ANVIL ? 1F : .7F);
  }

  private void click(Station station, int socket) {
    if (station.finished) return;
    DevItemStack stack = station.wrap();
    if (stack == null || !(stack.definition() instanceof SocketedVanillaItem definition)) return;
    Map<Integer, Map.Entry<DevEnchantment, EnchantmentData>> assigned = definition.normalize(stack);
    if (station.type == StationType.GRINDSTONE) {
      strip(station, definition, stack, socket, assigned);
    } else {
      enchantWithBook(station, definition, stack, socket, assigned);
    }
    stack.render(station.player);
    station.storage.setItem(0, stack.bukkitStack());
  }

  private void enchantWithBook(
    Station station, SocketedVanillaItem definition, DevItemStack stack, int socket,
    Map<Integer, Map.Entry<DevEnchantment, EnchantmentData>> assigned
  ) {
    if (assigned.containsKey(socket)) {
      station.player.sendMessage("§cThat socket is already enchanted.");
      return;
    }
    EnchantmentSlot slot = definition.sockets().get(socket);
    int cost = plugin.enchantingCosts().materialCost(stack.bukkitStack().getType());
    if (station.player.getLevel() < cost) {
      station.player.sendMessage("§cYou need " + cost + " levels.");
      return;
    }
    ItemStack[] contents = station.player.getInventory().getStorageContents();
    for (int index = 0; index < contents.length; index++) {
      ItemStack candidate = contents[index];
      if (candidate == null || candidate.getType() != Material.ENCHANTED_BOOK) continue;
      DevItemStack book = ItemsPlugin.instance().factory().wrap(candidate);
      for (Map.Entry<DevEnchantment, EnchantmentData> entry : book.enchantmentData().entrySet()) {
        DevEnchantment enchantment = entry.getKey();
        if (EnchantmentCatalog.category(enchantment) != slot.category()) continue;
        if (!EnchantmentCatalog.applicable(enchantment, stack.bukkitStack().getType())) continue;
        Map<NamespacedKey, Object> metadata = new HashMap<>(entry.getValue().metadata());
        metadata.put(SocketedVanillaItem.slotKey(), socket);
        metadata.put(SocketedVanillaItem.categoryKey(), slot.category().ordinal());
        try {
          definition.applySocketEnchantment(stack, enchantment,
            new EnchantmentData(entry.getValue().level(), metadata));
        } catch (RuntimeException exception) {
          continue;
        }
        if (candidate.getAmount() > 1) candidate.setAmount(candidate.getAmount() - 1);
        else contents[index] = null;
        station.player.getInventory().setStorageContents(contents);
        LinearExperience.removeLevels(station.player, cost);
        station.player.playSound(station.player.getLocation(), Sound.BLOCK_ANVIL_USE, .8F, 1.2F);
        return;
      }
    }
    station.player.sendMessage("§cNo matching enchanted book for that socket.");
  }

  private void strip(
    Station station, SocketedVanillaItem definition, DevItemStack stack, int socket,
    Map<Integer, Map.Entry<DevEnchantment, EnchantmentData>> assigned
  ) {
    if (!assigned.containsKey(socket)) {
      station.player.sendMessage("§7That socket is empty.");
      return;
    }
    definition.removeSocket(stack, socket);
    int refund = plugin.enchantingCosts().refund(stack.bukkitStack().getType());
    if (refund > 0) LinearExperience.addPoints(station.player, refund * LinearExperience.pointsPerLevel());
    station.player.playSound(station.player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, .8F, 1F);
  }

  private void repair(Station station) {
    DevItemStack stack = station.wrap();
    if (stack == null) return;
    ItemStack item = stack.bukkitStack();
    ItemMeta meta = item.getItemMeta();
    if (!(meta instanceof Damageable damageable) || damageable.getDamage() <= 0) {
      station.player.sendMessage("§7This item is at full durability.");
      return;
    }
    boolean mending = stack.enchantmentData().keySet().stream().anyMatch(SocketedVanillaItem::isMending);
    if (!mending) {
      Material material = repairMaterial(item.getType());
      int cost = plugin.enchantingCosts().materialCost(item.getType());
      if (material == null) {
        station.player.sendMessage("§cThis item cannot be repaired.");
        return;
      }
      if (!station.player.getInventory().containsAtLeast(new ItemStack(material), 1)) {
        station.player.sendMessage("§cYou need " + material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + ".");
        return;
      }
      if (station.player.getLevel() < cost) {
        station.player.sendMessage("§cYou need " + cost + " levels.");
        return;
      }
      station.player.getInventory().removeItem(new ItemStack(material, 1));
      LinearExperience.removeLevels(station.player, cost);
    }
    damageable.setDamage(0);
    item.setItemMeta(meta);
    station.storage.setItem(0, item);
    station.player.playSound(station.player.getLocation(), Sound.BLOCK_ANVIL_USE, .8F, 1.4F);
  }

  private ItemStack renderSocket(Station station, int socket, Player viewer) {
    DevItemStack stack = station.wrap();
    if (stack == null || !(stack.definition() instanceof SocketedVanillaItem definition)) return grayPane();
    EnchantmentSlot slot = definition.sockets().get(socket);
    Map<Integer, Map.Entry<DevEnchantment, EnchantmentData>> assigned = definition.normalize(stack);
    Map.Entry<DevEnchantment, EnchantmentData> filled = assigned.get(socket);
    ItemStack icon = new ItemStack(pane(slot.category()));
    ItemMeta meta = icon.getItemMeta();
    String color = slot.category().color();
    String icon1 = slot.category().icon();
    String label = color + "[ " + icon1 + " " + slot.category().displayName(viewer) + " ]";
    meta.setDisplayName(label);
    ArrayList<String> lore = new ArrayList<>();
    if (filled == null) {
      lore.add("§8Empty socket");
      lore.add(station.type == StationType.ANVIL
        ? "§7Drop a matching enchanted book here to enchant."
        : "§7Nothing to strip.");
    } else {
      lore.add("§7" + filled.getKey().properties().quality().colorCode() + filled.getKey().displayName(viewer)
        + " §f" + RomanNumerals.format(filled.getValue().level()));
      if (SocketedVanillaItem.isMending(filled.getKey()))
        lore.addAll(SocketedVanillaItem.renderMendingDescription(viewer));
      else
        lore.addAll(EnchantmentDisplay.renderDescription(filled.getKey(), filled.getValue(), viewer,
          stack.bukkitStack().getType()));
      if (station.type == StationType.GRINDSTONE) {
        lore.add("");
        lore.add("§eClick to strip §7(-"
          + plugin.enchantingCosts().refund(stack.bukkitStack().getType()) + " lvl)");
      }
    }
    meta.setLore(lore);
    icon.setItemMeta(meta);
    return icon;
  }

  private static Material pane(EnchantmentCategory category) {
    return switch (category) {
      case FATALITY -> Material.RED_STAINED_GLASS_PANE;
      case PROWESS -> Material.MAGENTA_STAINED_GLASS_PANE;
      case PROTECTION -> Material.LIME_STAINED_GLASS_PANE;
      case MOBILITY -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
      case TIDE -> Material.BLUE_STAINED_GLASS_PANE;
      case HARVESTING -> Material.ORANGE_STAINED_GLASS_PANE;
      case SUSTAINABILITY -> Material.YELLOW_STAINED_GLASS_PANE;
      case UNIVERSAL -> Material.WHITE_STAINED_GLASS_PANE;
    };
  }

  private static MenuItem filler() {
    return MenuItem.builder(Material.GRAY_STAINED_GLASS_PANE).localizedName("enchants.menu.blank").build();
  }

  private static ItemStack grayPane() {
    ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
    ItemMeta meta = pane.getItemMeta();
    meta.setDisplayName(" ");
    pane.setItemMeta(meta);
    return pane;
  }

  private void finish(Station station) {
    if (station.finished) return;
    station.finished = true;
    stations.remove(station.player.getUniqueId(), station);
    ItemStack item = station.storage.take();
    if (item != null) {
      station.player.getInventory().addItem(item).values().forEach(leftover ->
        station.player.getWorld().dropItemNaturally(station.player.getLocation(), leftover));
    }
  }

  private static boolean socketable(ItemStack item) {
    return item != null && !item.getType().isAir() && SocketedVanillaItem.supports(item.getType());
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
    if (name.equals("TURTLE_HELMET")) return Material.SCUTE;
    return null;
  }

  private enum StationType {
    ANVIL("Anvil"),
    GRINDSTONE("Grindstone");

    private final String title;
    StationType(String title) { this.title = title; }

    static StationType of(Material material) {
      return switch (material) {
        case ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL -> ANVIL;
        case GRINDSTONE -> GRINDSTONE;
        default -> null;
      };
    }
  }

  private final class Station {
    private final Player player;
    private final StationType type;
    private final InputStorage storage;
    private final int sockets;
    private MenuSession session;
    private boolean finished;

    private Station(Player player, StationType type, ItemStack item) {
      this.player = player;
      this.type = type;
      this.storage = new InputStorage(item);
      this.sockets = item == null ? 0 : socketCount(item);
    }

    private int sockets() { return sockets; }

    private DevItemStack wrap() {
      ItemStack item = storage.getItem(0);
      if (item == null) return null;
      try {
        return ItemsPlugin.instance().factory().wrap(item);
      } catch (RuntimeException exception) {
        return null;
      }
    }
  }

  private static int socketCount(ItemStack item) {
    try {
      DevItemStack stack = ItemsPlugin.instance().factory().wrap(item);
      return stack.definition() instanceof SocketedVanillaItem definition ? definition.sockets().size() : 0;
    } catch (RuntimeException exception) {
      return 0;
    }
  }

  private static final class InputStorage extends StorageProvider {
    private ItemStack item;
    private InputStorage(ItemStack item) { this.item = item == null ? null : item.clone(); }
    @Override public int size() { return 1; }
    @Override public ItemStack getItem(int index) { requireIndex(index); return item == null ? null : item.clone(); }
    @Override public void setItem(int index, ItemStack value) { requireIndex(index); item = value == null ? null : value.clone(); }
    private ItemStack take() { ItemStack result = item; item = null; return result; }
    private static void requireIndex(int index) { if (index != 0) throw new IndexOutOfBoundsException(index); }
  }
}
