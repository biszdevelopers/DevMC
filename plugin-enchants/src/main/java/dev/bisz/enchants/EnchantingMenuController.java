package dev.bisz.enchants;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.items.DevEnchantment;
import dev.bisz.items.DevItemStack;
import dev.bisz.items.EnchantmentData;
import dev.bisz.items.EnchantmentDisplay;
import dev.bisz.items.ItemsPlugin;
import dev.bisz.menus.MenuItem;
import dev.bisz.menus.MenuSession;
import dev.bisz.menus.SinglePageMenuTemplate;
import dev.bisz.menus.StorageProvider;
import dev.bisz.players.Profile;
import dev.bisz.players.locales.Locale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Owns enchanting-table interception, live sessions, rendering, and transactions. */
final class EnchantingMenuController implements Listener {
  private static final int[] OFFER_SLOTS = { 30, 31, 32 };
  private static final int[] BLACK_SLOTS = {
    0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 51, 52, 53,
  };
  private static final String PLAYER_SEED_KEY = "minecraft:enchanting_table/random_seed";
  private final EnchantsPlugin plugin;
  private final Map<UUID, ActiveMenu> activeMenus = new LinkedHashMap<>();
  private final Map<UUID, Long> playerSeeds = new LinkedHashMap<>();
  private final BukkitTask ticker;

  EnchantingMenuController(EnchantsPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
  }

  void dispose() {
    ticker.cancel();
    new ArrayList<>(activeMenus.values()).forEach(active -> {
      if (active.session != null) active.session.close();
    });
    activeMenus.clear();
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
    Block clicked = event.getClickedBlock();
    if (clicked == null || clicked.getType() != Material.ENCHANTING_TABLE) return;
    event.setCancelled(true);
    open(event.getPlayer(), clicked.getLocation());
  }

  private void open(Player player, Location table) {
    ActiveMenu active = new ActiveMenu(player, table, new InputStorage());
    active.seed = playerSeed(player);
    active.matches = scan(table);
    SinglePageMenuTemplate.Builder builder = SinglePageMenuTemplate.builder(
      viewer -> Locale.get(viewer, "enchants.menu.title"),
      6
    );
    for (int slot = 0; slot < 54; slot++) builder.item(slot, filler(Material.GRAY_STAINED_GLASS_PANE));
    for (int slot : BLACK_SLOTS) builder.item(slot, filler(Material.BLACK_STAINED_GLASS_PANE));
    builder.item(4, MenuItem.builder(Material.ENCHANTING_TABLE)
      .localizedName("enchants.menu.table.name")
      .wrappedLocalizedLore("enchants.menu.table.description")
      .build());
    builder.storageIndex(22, 0);
    for (int index = 0; index < OFFER_SLOTS.length; index++) {
      int offerIndex = index;
      builder.item(OFFER_SLOTS[index], MenuItem.dynamic(viewer -> renderOffer(active, offerIndex, viewer))
        .onClick(context -> enchant(active, offerIndex)));
    }
    builder.item(48, MenuItem.dynamic(viewer -> renderModifiers(active, viewer)));
    builder.item(49, MenuItem.builder(Material.BARRIER)
      .localizedName("locale.menu.close")
      .onClick(context -> context.session().close())
      .build());
    builder.item(50, MenuItem.dynamic(viewer -> renderLapis(viewer))
      .onClick(context -> disableAutoLapis(active)));
    builder.onStorageChange(context -> inputChanged(active));
    builder.onClose(context -> finish(active));
    active.session = BundlerPlugin.instance().menuManager().open(player, builder.build(), active.storage);
    activeMenus.put(player.getUniqueId(), active);
    player.playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 0.8f);
  }

  private MenuItem filler(Material material) {
    return MenuItem.builder(material).localizedName("enchants.menu.blank").build();
  }

  private void inputChanged(ActiveMenu active) {
    if (active.finished) return;
    ItemStack item = active.storage.getItem(0);
    if (item != null && item.getAmount() > 1) {
      ItemStack surplus = item.clone();
      surplus.setAmount(item.getAmount() - 1);
      item.setAmount(1);
      active.storage.setItem(0, item);
      giveOrDrop(active.player, surplus);
    }
    regenerate(active);
    active.session.refresh();
  }

  private void regenerate(ActiveMenu active) {
    ItemStack input = active.storage.getItem(0);
    active.offers = List.of();
    active.hints = List.of();
    if (input == null) return;
    try {
      RandomGenerator random = new Random(generationSeed(active, input));
      List<EnchantmentOffer> generated = plugin.enchantmentGenerator().generate(
        new EnchantingGenerationContext(input, data(active.matches), random)
      );
      if (!generated.isEmpty() && generated.size() != 3) throw new IllegalStateException(
        "Enchantment generators must return zero or exactly three offers"
      );
      active.offers = List.copyOf(generated);
      ArrayList<String> hints = new ArrayList<>(generated.size());
      for (int ignored = 0; ignored < generated.size(); ignored++) hints.add(randomHint(random));
      active.hints = List.copyOf(hints);
      if (!generated.isEmpty()) active.player.playSound(active.player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.2f);
    } catch (RuntimeException exception) {
      plugin.getLogger().log(Level.SEVERE, "Could not generate enchantment offers", exception);
    }
  }

  private ItemStack renderOffer(ActiveMenu active, int index, Player viewer) {
    if (index >= active.offers.size()) return placeholder(active, viewer);
    EnchantmentOffer offer = active.offers.get(index);
    ItemStack icon = new ItemStack(Material.ENCHANTED_BOOK);
    ItemMeta meta = icon.getItemMeta();
    meta.setDisplayName(Locale.get(viewer, "enchants.offer.name", active.hints.get(index)));
    ArrayList<String> lore = new ArrayList<>();
    for (var entry : EnchantmentDisplay.order(offer.baseEnchantments(), viewer)) {
      lore.add(EnchantmentDisplay.renderLine(entry.getKey(), entry.getValue(), viewer));
    }
    offer.extraEnchantments().entrySet().stream()
      .sorted(Comparator.comparing(entry -> ChatColor.stripColor(entry.getKey().displayName(viewer)), String.CASE_INSENSITIVE_ORDER))
      .map(entry -> EnchantmentDisplay.renderLine(entry.getKey(), entry.getValue(), viewer))
      .forEach(lore::add);
    lore.add(Locale.get(viewer, "enchants.offer.extra_cap", DefaultEnchantmentGenerator.extraRollCap(index)));
    lore.add("");
    lore.add("§7§m--------------------");
    lore.add(Locale.get(viewer, "enchants.offer.lapis", offer.lapisLazuli()));
    lore.add(Locale.get(viewer, "enchants.offer.levels", offer.experienceLevels()));
    lore.add("");
    lore.add(requirementsMet(viewer, offer)
      ? Locale.get(viewer, "enchants.offer.click")
      : Locale.get(viewer, "enchants.offer.unmet"));
    meta.setLore(lore);
    icon.setItemMeta(meta);
    return icon;
  }

  private ItemStack placeholder(ActiveMenu active, Player viewer) {
    ItemStack icon = new ItemStack(Material.BOOK);
    ItemMeta meta = icon.getItemMeta();
    meta.setDisplayName(Locale.get(viewer, "enchants.offer.empty.name"));
    ItemStack input = active.storage.getItem(0);
    String key = "enchants.offer.empty.place_item";
    if (input != null) {
      try {
        key = ItemsPlugin.instance().factory().wrap(input).enchantments().isEmpty()
          ? "enchants.offer.empty.unsupported"
          : "enchants.offer.empty.already_enchanted";
      } catch (RuntimeException ignored) {
        key = "enchants.offer.empty.unsupported";
      }
    }
    meta.setLore(wrap(viewer, Locale.get(viewer, key), false));
    icon.setItemMeta(meta);
    return icon;
  }

  private ItemStack renderModifiers(ActiveMenu active, Player viewer) {
    ItemStack icon = new ItemStack(Material.BOOKSHELF);
    ItemMeta meta = icon.getItemMeta();
    meta.setDisplayName(Locale.get(viewer, "enchants.modifier.name"));
    ArrayList<String> lore = new ArrayList<>(wrap(viewer, Locale.get(viewer, "enchants.modifier.introduction"), false));
    active.matches.stream().map(ModifierMatch::modifier).map(CustomEnchantingModifier::mode).distinct().forEach(mode ->
      lore.add(Locale.get(viewer, mode == ModifierMode.PERMANENT
        ? "enchants.modifier.legend.permanent"
        : "enchants.modifier.legend.consumable"))
    );
    List<ModifierMatch> present = active.matches.stream().filter(match -> match.data().blockCount() > 0).toList();
    if (present.isEmpty()) {
      lore.add("");
      lore.add(Locale.get(viewer, "enchants.modifier.none"));
    } else present.forEach(match -> lore.addAll(match.modifier().renderLore(viewer, match.data())));
    meta.setLore(lore);
    icon.setItemMeta(meta);
    return icon;
  }

  private ItemStack renderLapis(Player viewer) {
    ItemStack icon = new ItemStack(Material.LAPIS_LAZULI);
    ItemMeta meta = icon.getItemMeta();
    meta.setDisplayName(Locale.get(viewer, "enchants.lapis.name"));
    ArrayList<String> lore = new ArrayList<>(wrap(viewer, Locale.get(viewer, "enchants.lapis.description"), false));
    lore.add("");
    lore.add(Locale.get(viewer, "enchants.lapis.status"));
    lore.addAll(wrap(viewer, Locale.get(viewer, "enchants.lapis.unavailable"), false));
    meta.setLore(lore);
    icon.setItemMeta(meta);
    return icon;
  }

  private void disableAutoLapis(ActiveMenu active) {
    Profile.setMetadata(active.player.getUniqueId(), "minecraft:enchanting_table/auto_consume_lapis", false)
      .whenComplete((profile, failure) -> {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
          if (failure != null && active.player.isOnline()) active.player.sendMessage(Locale.get(active.player, "enchants.lapis.save_failed"));
          if (!active.finished) active.session.refresh();
        });
      });
  }

  private void enchant(ActiveMenu active, int index) {
    if (active.finished || index >= active.offers.size()) return;
    List<ModifierMatch> currentMatches = scan(active.table);
    if (!data(currentMatches).equals(data(active.matches))) {
      active.matches = currentMatches;
      regenerate(active);
      active.session.refresh();
      return;
    }
    ItemStack input = active.storage.getItem(0);
    if (input == null) return;
    EnchantmentOffer offer = active.offers.get(index);
    if (!requirementsMet(active.player, offer)) {
      active.player.playSound(active.player, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
      active.session.refresh();
      return;
    }
    try {
      DevItemStack original = ItemsPlugin.instance().factory().wrap(input);
      if (!original.enchantments().isEmpty()) {
        regenerate(active); active.session.refresh(); return;
      }
      ItemStack result = input.clone();
      if (result.getType() == Material.BOOK) result.setType(Material.ENCHANTED_BOOK);
      DevItemStack enchanted = ItemsPlugin.instance().factory().wrap(result);
      offer.enchantments().forEach((enchantment, enchantmentData) ->
        enchanted.enchant(enchantment, enchantmentData.level(), enchantmentData.metadata())
      );
      enchanted.render(active.player);
      takeLapis(active.player, offer.lapisLazuli());
      active.player.giveExpLevels(-offer.experienceLevels());
      active.storage.setItem(0, enchanted.bukkitStack());
      active.matches.forEach(match -> match.modifier().consume(match.blocks()));
      active.player.playSound(active.player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
      advanceSeed(active);
      active.matches = scan(active.table);
      regenerate(active);
      active.session.refresh();
    } catch (RuntimeException exception) {
      plugin.getLogger().log(Level.SEVERE, "Could not complete enchantment", exception);
      active.player.sendMessage(Locale.get(active.player, "enchants.offer.failure"));
    }
  }

  private void tick() {
    for (ActiveMenu active : new ArrayList<>(activeMenus.values())) {
      if (!valid(active)) {
        active.session.close();
        continue;
      }
      List<ModifierMatch> matches = scan(active.table);
      if (!data(matches).equals(data(active.matches))) {
        active.matches = matches;
        regenerate(active);
        active.session.refresh();
      }
    }
  }

  private boolean valid(ActiveMenu active) {
    if (!active.player.isOnline() || active.table.getBlock().getType() != Material.ENCHANTING_TABLE) return false;
    if (!active.player.getWorld().equals(active.table.getWorld())) return false;
    return active.player.getLocation().distanceSquared(active.table.clone().add(.5, .5, .5)) <= 64.0;
  }

  private List<ModifierMatch> scan(Location table) {
    ArrayList<Block> blocks = new ArrayList<>(125);
    for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++)
      blocks.add(table.getWorld().getBlockAt(table.getBlockX() + x, table.getBlockY() + y, table.getBlockZ() + z));
    return plugin.enchantingModifiers().stream().map(modifier -> modifier.scan(blocks)).toList();
  }

  private static List<EnchantingModifierData> data(List<ModifierMatch> matches) {
    return matches.stream().map(ModifierMatch::data).toList();
  }

  private void finish(ActiveMenu active) {
    if (active.finished) return;
    active.finished = true;
    activeMenus.remove(active.player.getUniqueId(), active);
    ItemStack item = active.storage.take();
    if (item != null) giveOrDrop(active.player, item);
  }

  private static boolean requirementsMet(Player player, EnchantmentOffer offer) {
    return player.getLevel() >= offer.experienceLevels() && countLapis(player) >= offer.lapisLazuli();
  }

  private static int countLapis(Player player) {
    int count = 0;
    for (ItemStack item : player.getInventory().getStorageContents())
      if (item != null && item.getType() == Material.LAPIS_LAZULI) count += item.getAmount();
    return count;
  }

  private static void takeLapis(Player player, int amount) {
    ItemStack[] contents = player.getInventory().getStorageContents();
    for (int slot = 0; slot < contents.length && amount > 0; slot++) {
      ItemStack item = contents[slot];
      if (item == null || item.getType() != Material.LAPIS_LAZULI) continue;
      int removed = Math.min(amount, item.getAmount());
      item.setAmount(item.getAmount() - removed);
      contents[slot] = item.getAmount() == 0 ? null : item;
      amount -= removed;
    }
    player.getInventory().setStorageContents(contents);
  }

  private static void giveOrDrop(Player player, ItemStack item) {
    player.getInventory().addItem(item).values().forEach(leftover ->
      player.getWorld().dropItemNaturally(player.getLocation(), leftover)
    );
  }

  private static List<String> wrap(Player player, String text, boolean indented) {
    int width = switch (Locale.getLanguage(player)) {
      case ZH_CN, ZH_CN_MOJANG, ZH_TW, JA_JP, KO_KR -> indented ? 13 : 15;
      default -> indented ? 28 : 30;
    };
    ArrayList<String> lines = new ArrayList<>();
    for (String line : dev.bisz.chat.ChatUtils.wrapTextColor(text, width).split("\\R", -1))
      lines.add((indented ? "  " : "") + line);
    return lines;
  }

  private static String randomHint(RandomGenerator random) {
    int words = random.nextInt(1, 4);
    StringBuilder result = new StringBuilder();
    for (int word = 0; word < words; word++) {
      if (word > 0) result.append(' ');
      int length = random.nextInt(3, 9);
      for (int index = 0; index < length; index++) result.append((char) ('a' + random.nextInt(26)));
    }
    return result.toString();
  }

  private long playerSeed(Player player) {
    Long remembered = playerSeeds.get(player.getUniqueId());
    if (remembered != null) return remembered;
    var profile = Profile.findByOwner(player);
    Object stored = profile == null ? null : profile.getMetadata(PLAYER_SEED_KEY);
    if (stored instanceof Number number) {
      long seed = number.longValue();
      playerSeeds.put(player.getUniqueId(), seed);
      return seed;
    }
    long created = mix64(player.getUniqueId().getMostSignificantBits() ^ player.getUniqueId().getLeastSignificantBits());
    playerSeeds.put(player.getUniqueId(), created);
    Profile.setMetadata(player.getUniqueId(), PLAYER_SEED_KEY, created).exceptionally(failure -> {
      plugin.getLogger().log(Level.WARNING, "Could not persist initial enchanting seed for " + player.getName(), failure);
      return null;
    });
    return created;
  }

  private long generationSeed(ActiveMenu active, ItemStack item) {
    long itemHash = item.getType().getKey().toString().hashCode();
    try {
      DevItemStack wrapped = ItemsPlugin.instance().factory().wrap(item);
      itemHash = 31L * itemHash + wrapped.definition().id().toString().hashCode();
      itemHash = 31L * itemHash + wrapped.uniqueId().map(UUID::hashCode).orElse(0);
    } catch (RuntimeException ignored) {}
    return mix64(active.seed ^ itemHash);
  }

  private void advanceSeed(ActiveMenu active) {
    active.seed = mix64(active.seed + 0x9E3779B97F4A7C15L);
    playerSeeds.put(active.player.getUniqueId(), active.seed);
    Profile.setMetadata(active.player.getUniqueId(), PLAYER_SEED_KEY, active.seed).exceptionally(failure -> {
      plugin.getLogger().log(Level.WARNING, "Could not persist enchanting seed for " + active.player.getName(), failure);
      return null;
    });
  }

  private static long mix64(long value) {
    value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
    value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
    return value ^ (value >>> 31);
  }

  private static final class InputStorage extends StorageProvider {
    private ItemStack item;
    @Override public int size() { return 1; }
    @Override public ItemStack getItem(int index) { requireIndex(index); return item == null ? null : item.clone(); }
    @Override public void setItem(int index, ItemStack value) { requireIndex(index); item = value == null ? null : value.clone(); }
    ItemStack take() { ItemStack result = item; item = null; return result; }
    private static void requireIndex(int index) { if (index != 0) throw new IndexOutOfBoundsException(index); }
  }

  private static final class ActiveMenu {
    private final Player player;
    private final Location table;
    private final InputStorage storage;
    private MenuSession session;
    private List<ModifierMatch> matches = List.of();
    private List<EnchantmentOffer> offers = List.of();
    private List<String> hints = List.of();
    private long seed;
    private boolean finished;
    private ActiveMenu(Player player, Location table, InputStorage storage) {
      this.player = player; this.table = table.clone(); this.storage = storage;
    }
  }
}
