package dev.bisz.enchants;

import dev.bisz.items.Ability;
import dev.bisz.items.AbilityDisplay;
import dev.bisz.items.AbilityItem;
import dev.bisz.items.DevEnchantment;
import dev.bisz.items.DevItemStack;
import dev.bisz.items.EnchantmentData;
import dev.bisz.items.EnchantmentDisplay;
import dev.bisz.items.EnchantmentId;
import dev.bisz.items.ItemsPlugin;
import dev.bisz.items.ItemProperties;
import dev.bisz.items.OverrideDamageableVanillaItem;
import dev.bisz.enchants.items.NimbleEnchantment;
import dev.bisz.enchants.items.SocketDisplayRenderer;
import dev.bisz.enchants.items.WingedEnchantment;
import dev.bisz.items.RomanNumerals;
import dev.bisz.players.locales.Locale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Vanilla definition with a material-specific ordered typed socket layout. */
public class SocketedVanillaItem extends OverrideDamageableVanillaItem implements AbilityItem {
  private static final NamespacedKey SLOT_KEY = Objects.requireNonNull(NamespacedKey.fromString("enchants:slot"));
  private static final NamespacedKey CATEGORY_KEY = Objects.requireNonNull(NamespacedKey.fromString("enchants:socket_category"));
  private static final NamespacedKey SOCKET_ENTRIES_KEY = Objects.requireNonNull(NamespacedKey.fromString("enchants:socket_entries"));
  private static final NamespacedKey MENDING_COUNT_KEY = Objects.requireNonNull(NamespacedKey.fromString("enchants:mend_count"));
  private static final NamespacedKey MENDING_REMAINDER_KEY = Objects.requireNonNull(NamespacedKey.fromString("enchants:mend_remainder"));
  private static final NamespacedKey LEGACY_MENDING_REMAINING_KEY = Objects.requireNonNull(NamespacedKey.fromString("enchants:remaining_mends"));
  private static final int[] MENDING_THRESHOLDS = {
    0, 2_000, 22_000, 42_000, 62_000, 112_000, 162_000, 212_000, 312_000, 512_000, 912_000
  };
  static final int MENDING_MAX_LEVEL = MENDING_THRESHOLDS.length;
  private static final Comparator<Map.Entry<DevEnchantment, EnchantmentData>> PRIORITY = (left, right) -> {
    int quality = Integer.compare(right.getKey().properties().quality().ordinal(), left.getKey().properties().quality().ordinal());
    if (quality != 0) return quality;
    int level = Integer.compare(right.getValue().level(), left.getValue().level());
    return level != 0 ? level : left.getKey().id().toString().compareTo(right.getKey().id().toString());
  };
  private final List<EnchantmentSlot> sockets;

  protected SocketedVanillaItem(Material material) {
    super(material, ItemProperties.builder(material).build(), material == Material.BOOK || material == Material.ENCHANTED_BOOK);
    List<EnchantmentCategory> layout = SocketLayouts.forMaterial(material);
    ArrayList<EnchantmentSlot> values = new ArrayList<>(layout.size());
    for (int index = 0; index < layout.size(); index++) values.add(layout.get(index) == EnchantmentCategory.UNIVERSAL
      ? EnchantmentSlot.universal(index) : EnchantmentSlot.typed(index, layout.get(index)));
    sockets = List.copyOf(values);
  }

  public List<EnchantmentSlot> sockets() { return sockets; }
  @Override protected void onLoaded(DevItemStack stack) { normalize(stack); }
  @Override protected List<String> renderEnchantmentLore(DevItemStack stack, Player viewer) { return renderSockets(normalize(stack), viewer); }
  @Override public List<Ability> abilities(DevItemStack stack) {
    return abilitiesFor(material(), stack.enchantmentData().keySet());
  }

  static List<Ability> abilitiesFor(Iterable<? extends DevEnchantment> enchantments) {
    for (DevEnchantment enchantment : enchantments)
      if (enchantment.id().equals(WingedEnchantment.ID)) return List.of(WingedEnchantment.DOUBLE_JUMP);
    return List.of();
  }

  static List<Ability> abilitiesFor(
    Iterable<? extends DevEnchantment> current,
    Iterable<? extends DevEnchantment> proposed
  ) {
    List<Ability> abilities = abilitiesFor(current);
    return abilities.isEmpty() ? abilitiesFor(proposed) : abilities;
  }

  static List<Ability> abilitiesFor(Material material, Iterable<? extends DevEnchantment> enchantments) {
    ArrayList<Ability> abilities = new ArrayList<>();
    for (DevEnchantment enchantment : enchantments) {
      if (enchantment.id().equals(WingedEnchantment.ID) && material.name().endsWith("BOOTS")
        && !abilities.contains(WingedEnchantment.DOUBLE_JUMP))
        abilities.add(WingedEnchantment.DOUBLE_JUMP);
      if (enchantment.id().equals(NimbleEnchantment.ID) && material == Material.BOW
        && !abilities.contains(NimbleEnchantment.SHORTBOW))
        abilities.add(NimbleEnchantment.SHORTBOW);
      if (RiptideFishingRodSpecialty.isRiptide(enchantment) && material == Material.FISHING_ROD
        && !abilities.contains(RiptideFishingRodSpecialty.GRAPPLE))
        abilities.add(RiptideFishingRodSpecialty.GRAPPLE);
    }
    return List.copyOf(abilities);
  }

  List<String> renderSockets(Map<Integer, ? extends Map.Entry<DevEnchantment, EnchantmentData>> assigned, Player viewer) {
    ArrayList<String> lines = new ArrayList<>();
    boolean descriptions = assigned.size() <= 5;
    for (EnchantmentSlot socket : sockets) {
      Map.Entry<DevEnchantment, EnchantmentData> entry = assigned.get(socket.index());
      if (entry == null) {
        lines.add(SocketDisplayRenderer.empty("§7", socket.category().icon(), viewer));
        continue;
      }
      // A filled socket begins its own lore block even when the socket directly
      // above it is empty. Filled sections already separated by a description's
      // trailing blank line must not receive a second separator.
      separateFilledSocket(lines);
      lines.add(SocketDisplayRenderer.filled(socket.category().color(), socket.category().icon(), entry, viewer));
      if (isMending(entry.getKey())) {
        if (descriptions) {
          lines.addAll(renderMendingDescription(viewer));
          lines.addAll(renderMendingProgress(entry.getValue(), viewer));
        }
        lines.add(renderMendingStat(entry.getValue(), viewer));
      } else if (descriptions) {
        lines.addAll(EnchantmentDisplay.renderDescription(entry.getKey(), entry.getValue(), viewer, material()));
      }
      finishFilledSocket(lines, socket.index() + 1 < sockets.size());
    }
    return List.copyOf(lines);
  }

  List<String> renderOfferSockets(
    DevItemStack current,
    Map<DevEnchantment, EnchantmentData> enchantments,
    Player viewer
  ) {
    return renderOfferSockets(current, enchantments.entrySet().stream()
      .map(entry -> new EnchantmentSelection(entry.getKey(), entry.getValue())).toList(), viewer);
  }

  List<String> renderOfferSockets(
    DevItemStack current,
    List<EnchantmentSelection> enchantments,
    Player viewer
  ) {
    HashMap<Integer, Map.Entry<DevEnchantment, EnchantmentData>> assigned =
      new HashMap<>(normalize(current));
    enchantments.forEach(selection -> {
      Object raw = selection.data().metadata().get(SLOT_KEY);
      if (raw instanceof Integer index && index >= 0 && index < sockets.size())
        assigned.put(index, Map.entry(selection.enchantment(), selection.data()));
    });
    return renderSockets(assigned, viewer);
  }

  void applySocketEnchantment(DevItemStack stack, DevEnchantment enchantment, EnchantmentData data) {
    HashMap<Integer, Map.Entry<DevEnchantment, EnchantmentData>> assigned = new HashMap<>(normalize(stack));
    if (!EnchantmentCatalog.repeatable(enchantment) && assigned.values().stream()
      .anyMatch(entry -> entry.getKey().id().equals(enchantment.id())))
      throw new IllegalArgumentException("Only Efficiency may occupy repeated sockets");
    Object raw = data.metadata().get(SLOT_KEY);
    if (!(raw instanceof Integer slot) || slot < 0 || slot >= sockets.size() || assigned.containsKey(slot)
      || !sockets.get(slot).accepts(enchantment))
      throw new IllegalArgumentException("Offer does not target an available socket");
    assigned.put(slot, Map.entry(enchantment, data));
    synchronizeTotals(stack, assigned);
    ensureMendingMetadata(stack);
    writeSocketEntries(stack, assigned);
  }

  /** Removes an enchantment and reconciles the serialized socket list. */
  void removeSocketEnchantment(DevItemStack stack, DevEnchantment enchantment) {
    stack.removeEnchantment(enchantment);
    normalize(stack);
  }

  static NamespacedKey mendingCountKey() { return MENDING_COUNT_KEY; }
  static NamespacedKey mendingRemainderKey() { return MENDING_REMAINDER_KEY; }
  static boolean isMending(DevEnchantment enchantment) { return enchantment.id().namespace().equals("minecraft") && enchantment.id().path().equals("mending"); }

  /** Total durability points this Mending has repaired; every Mending starts at zero. */
  static int mendCount(EnchantmentData data) {
    Object value = data.metadata().get(MENDING_COUNT_KEY);
    return value instanceof Integer number ? Math.max(0, number) : 0;
  }

  /** Fractional mending progress in tenths of a durability point, carried between mends. */
  static int mendRemainder(EnchantmentData data) {
    Object value = data.metadata().get(MENDING_REMAINDER_KEY);
    return value instanceof Integer number ? Math.max(0, Math.min(9, number)) : 0;
  }

  static int mendingLevel(int count) {
    int level = 1;
    for (int index = 1; index < MENDING_MAX_LEVEL && count >= MENDING_THRESHOLDS[index]; index++) level = index + 1;
    return level;
  }

  static int mendingMultiplierTenths(int level) {
    int clamped = Math.max(1, Math.min(MENDING_MAX_LEVEL, level));
    return 10 + (clamped - 1);
  }

  static double mendingMultiplier(int level) {
    return mendingMultiplierTenths(level) / 10D;
  }

  static int nextMendingThreshold(int level) {
    return level < 1 || level >= MENDING_MAX_LEVEL ? -1 : MENDING_THRESHOLDS[level];
  }

  /** One scaled mend: the points applied plus the fractional tenths carried to the next mend. */
  record MendingRepair(int repaired, int remainder) {}

  /**
   * Scales a base repair by the current multiplier in exact tenths and carries the
   * leftover fraction, so a x1.1 multiplier adds one point every fifth two-point mend
   * instead of rounding the bonus away.
   */
  static MendingRepair scaleMendingRepair(int baseRepair, int currentDamage, int count, int remainder) {
    int tenths = Math.max(0, baseRepair) * mendingMultiplierTenths(mendingLevel(count))
      + Math.max(0, Math.min(9, remainder));
    int repaired = Math.max(0, Math.min(tenths / 10, Math.max(0, currentDamage)));
    return new MendingRepair(repaired, tenths % 10);
  }

  static String multiplierText(int level) {
    return String.format(java.util.Locale.US, "%.1f", mendingMultiplier(level));
  }

  static List<String> renderMendingDescription(Player viewer) {
    return EnchantmentDisplay.wrapGray(locale(viewer, "enchants.mending.description",
      "Uses collected experience to repair the item. Every point mended raises this item's Mending, improving how much durability it restores per orb."), viewer);
  }

  /** Dark gray current tier plus the next tier, shown above the mend stat. */
  static List<String> renderMendingProgress(EnchantmentData data, Player viewer) {
    int count = mendCount(data);
    int level = mendingLevel(count);
    ArrayList<String> lines = new ArrayList<>();
    lines.add(locale(viewer, "enchants.mending.current", RomanNumerals.format(level), multiplierText(level)));
    if (level < MENDING_MAX_LEVEL)
      lines.add(locale(viewer, "enchants.mending.next", RomanNumerals.format(level + 1),
        String.format(java.util.Locale.US, "%,d", nextMendingThreshold(level)), multiplierText(level + 1)));
    return List.copyOf(lines);
  }

  static String renderMendingStat(EnchantmentData data, Player viewer) {
    return locale(viewer, "enchants.mending.stat", String.format(java.util.Locale.US, "%,d", mendCount(data)));
  }

  private static void ensureMendingMetadata(DevItemStack stack) {
    stack.enchantmentData().forEach((enchantment, data) -> {
      if (!isMending(enchantment)) return;
      if (data.metadata().containsKey(LEGACY_MENDING_REMAINING_KEY))
        stack.removeEnchantmentMetadata(enchantment, LEGACY_MENDING_REMAINING_KEY);
      Object existing = data.metadata().get(MENDING_COUNT_KEY);
      int normalized = existing instanceof Integer number ? Math.max(0, number) : 0;
      if (!Integer.valueOf(normalized).equals(existing)) stack.setEnchantmentMetadata(enchantment, MENDING_COUNT_KEY, normalized);
      Object existingRemainder = data.metadata().get(MENDING_REMAINDER_KEY);
      if (existingRemainder instanceof Integer number) {
        int normalizedRemainder = Math.max(0, Math.min(9, number));
        if (normalizedRemainder != number) stack.setEnchantmentMetadata(enchantment, MENDING_REMAINDER_KEY, normalizedRemainder);
      }
      int level = mendingLevel(normalized);
      if (data.level() != level) stack.enchant(enchantment, level);
    });
  }

  List<String> renderOfferAbilities(
    DevItemStack current,
    Map<DevEnchantment, EnchantmentData> enchantments,
    Player viewer
  ) {
    return renderOfferAbilities(current, enchantments.entrySet().stream()
      .map(entry -> new EnchantmentSelection(entry.getKey(), entry.getValue())).toList(), viewer);
  }

  List<String> renderOfferAbilities(
    DevItemStack current,
    List<EnchantmentSelection> enchantments,
    Player viewer
  ) {
    ArrayList<DevEnchantment> merged = new ArrayList<>(current.enchantmentData().keySet());
    enchantments.stream().map(EnchantmentSelection::enchantment).forEach(merged::add);
    return AbilityDisplay.render(abilitiesFor(material(), merged), viewer);
  }

  /** Clears the unreleased generic schema and repairs all typed assignments. */
  Map<Integer, Map.Entry<DevEnchantment, EnchantmentData>> normalize(DevItemStack stack) {
    Objects.requireNonNull(stack, "stack");
    ensureMendingMetadata(stack);
    adoptVanillaEnchantments(stack);
    List<Map.Entry<DevEnchantment, EnchantmentData>> stored = readSocketEntries(stack);
    if (stored != null) return normalizeEntries(stack, stored);
    ArrayList<Map.Entry<DevEnchantment, EnchantmentData>> ranked = new ArrayList<>(stack.enchantmentData().entrySet());
    boolean oldSchema = ranked.stream().anyMatch(entry -> entry.getValue().metadata().containsKey(SLOT_KEY)
      && !entry.getValue().metadata().containsKey(CATEGORY_KEY));
    if (oldSchema) {
      ranked.forEach(entry -> stack.removeEnchantment(entry.getKey()));
      return Map.of();
    }
    ranked.sort(PRIORITY);
    HashMap<Integer, Map.Entry<DevEnchantment, EnchantmentData>> assigned = new HashMap<>();
    ArrayList<Map.Entry<DevEnchantment, EnchantmentData>> pending = new ArrayList<>();
    for (Map.Entry<DevEnchantment, EnchantmentData> entry : ranked) {
      EnchantmentCategory category = EnchantmentCatalog.category(entry.getKey());
      if (category == null || !acceptsOnItem(entry.getKey())) {
        stack.removeEnchantment(entry.getKey());
        continue;
      }
      Object rawIndex = entry.getValue().metadata().get(SLOT_KEY);
      Object rawCategory = entry.getValue().metadata().get(CATEGORY_KEY);
      int index = rawIndex instanceof Integer value ? value : -1;
      boolean valid = index >= 0 && index < sockets.size() && !assigned.containsKey(index)
        && sockets.get(index).accepts(entry.getKey())
        && (!(rawCategory instanceof Integer value) || value == sockets.get(index).category().ordinal());
      if (valid) assigned.put(index, entry); else pending.add(entry);
    }
    for (Map.Entry<DevEnchantment, EnchantmentData> entry : pending) {
      int index = firstFree(assigned, entry.getKey());
      if (index < 0) { stack.removeEnchantment(entry.getKey()); continue; }
      assigned.put(index, entry);
    }
    assigned.forEach((index, entry) -> {
      if (!Integer.valueOf(index).equals(entry.getValue().metadata().get(SLOT_KEY))) stack.setEnchantmentMetadata(entry.getKey(), SLOT_KEY, index);
      if (!Integer.valueOf(sockets.get(index).category().ordinal()).equals(entry.getValue().metadata().get(CATEGORY_KEY)))
        stack.setEnchantmentMetadata(entry.getKey(), CATEGORY_KEY, sockets.get(index).category().ordinal());
    });
    writeSocketEntries(stack, assigned);
    return Map.copyOf(assigned);
  }

  int availableSlots(DevItemStack stack) { return sockets.size() - normalize(stack).size(); }
  List<EnchantmentSlot> freeSlots(DevItemStack stack) {
    Map<Integer, ?> assigned = normalize(stack);
    return sockets.stream().filter(slot -> !assigned.containsKey(slot.index())).toList();
  }

  /** Removes every socketed enchantment and clears the serialized socket list. */
  void stripAll(DevItemStack stack, Player viewer) {
    for (DevEnchantment enchantment : new ArrayList<>(stack.enchantmentData().keySet())) {
      if (EnchantmentCatalog.offered(enchantment)) stack.removeEnchantment(enchantment);
    }
    ItemMeta meta = stack.bukkitStack().getItemMeta();
    if (meta != null) {
      meta.getPersistentDataContainer().set(SOCKET_ENTRIES_KEY, PersistentDataType.STRING, "");
      stack.bukkitStack().setItemMeta(meta);
    }
    stack.render(viewer);
  }
  static NamespacedKey slotKey() { return SLOT_KEY; }
  static NamespacedKey categoryKey() { return CATEGORY_KEY; }
  static boolean supports(Material material) { return material.isItem() && !SocketLayouts.forMaterial(material).isEmpty(); }

  /**
   * Converts mapped vanilla enchantments (e.g. Sharpness) into their trueMC
   * catalog enchantment (e.g. Lethality) before socket assignment, matching
   * trueMC's item normalization.
   */
  private static void adoptVanillaEnchantments(DevItemStack stack) {
    Map<EnchantmentId, Integer> additions = new HashMap<>();
    ArrayList<DevEnchantment> removals = new ArrayList<>();
    for (Map.Entry<DevEnchantment, Integer> entry : stack.enchantments().entrySet()) {
      DevEnchantment enchantment = entry.getKey();
      if (!enchantment.vanilla()) continue;
      EnchantmentId targetId = EnchantmentCatalog.vanillaTarget(enchantment.id().path());
      if (targetId == null || targetId.equals(enchantment.id())) continue;
      DevEnchantment target = ItemsPlugin.instance().enchantments().get(targetId).orElse(null);
      if (target == null) continue;
      additions.merge(targetId, entry.getValue(), Integer::sum);
      removals.add(enchantment);
    }
    if (removals.isEmpty()) return;
    removals.forEach(stack::removeEnchantment);
    additions.forEach((id, level) -> ItemsPlugin.instance().enchantments().get(id).ifPresent(target ->
      stack.enchant(target, Math.min(level, EnchantmentCatalog.maximumLevel(target)))));
  }

  private Map<Integer, Map.Entry<DevEnchantment, EnchantmentData>> normalizeEntries(
    DevItemStack stack, List<Map.Entry<DevEnchantment, EnchantmentData>> entries
  ) {
    ArrayList<Map.Entry<DevEnchantment, EnchantmentData>> ranked = new ArrayList<>(entries);
    reconcileStoredLevels(stack, ranked);
    // The serialized socket list is authoritative for duplicate placement, but
    // commands and other ItemLib integrations add to the normal enchantment
    // payload first. Adopt those previously-unrepresented enchantments before
    // synchronizing totals, otherwise normalization would immediately erase
    // `/devenchant` and API additions.
    appendUnstoredEnchantments(ranked, stack.enchantmentData());
    collapseNonRepeatableEnchantments(ranked);
    ranked.sort(PRIORITY);
    HashMap<Integer, Map.Entry<DevEnchantment, EnchantmentData>> assigned = new HashMap<>();
    ArrayList<Map.Entry<DevEnchantment, EnchantmentData>> pending = new ArrayList<>();
    for (Map.Entry<DevEnchantment, EnchantmentData> entry : ranked) {
      EnchantmentCategory category = EnchantmentCatalog.category(entry.getKey());
      if (category == null || !acceptsOnItem(entry.getKey())) continue;
      Object raw = entry.getValue().metadata().get(SLOT_KEY);
      int index = raw instanceof Integer value ? value : -1;
      if (index >= 0 && index < sockets.size() && !assigned.containsKey(index) && sockets.get(index).accepts(entry.getKey()))
        assigned.put(index, entry);
      else pending.add(entry);
    }
    for (Map.Entry<DevEnchantment, EnchantmentData> entry : pending) {
      int index = firstFree(assigned, entry.getKey());
      if (index >= 0) assigned.put(index, entry);
    }
    synchronizeTotals(stack, assigned);
    Map<DevEnchantment, EnchantmentData> actual = stack.enchantmentData();
    assigned.replaceAll((index, entry) -> {
      EnchantmentData current = actual.get(entry.getKey());
      if (current == null) return entry;
      Map<NamespacedKey, Object> metadata = new HashMap<>(entry.getValue().metadata());
      metadata.putAll(current.metadata());
      return Map.entry(entry.getKey(), new EnchantmentData(entry.getValue().level(), metadata));
    });
    writeSocketEntries(stack, assigned);
    return Map.copyOf(assigned);
  }

  private static void reconcileStoredLevels(
    DevItemStack stack, List<Map.Entry<DevEnchantment, EnchantmentData>> entries
  ) {
    for (DevEnchantment enchantment : entries.stream().map(Map.Entry::getKey).distinct().toList()) {
      int stored = entries.stream().filter(entry -> entry.getKey().equals(enchantment)).mapToInt(entry -> entry.getValue().level()).sum();
      int actual = stack.enchantmentLevel(enchantment).orElse(0);
      if (actual == stored) continue;
      if (actual <= 0) {
        entries.removeIf(entry -> entry.getKey().equals(enchantment));
        continue;
      }
      if (actual > stored) {
        for (int index = 0; index < entries.size(); index++) {
          Map.Entry<DevEnchantment, EnchantmentData> entry = entries.get(index);
          if (entry.getKey().equals(enchantment)) {
            entries.set(index, Map.entry(enchantment, new EnchantmentData(
              entry.getValue().level() + actual - stored, entry.getValue().metadata())));
            break;
          }
        }
        continue;
      }
      int excess = stored - actual;
      for (int index = entries.size() - 1; index >= 0 && excess > 0; index--) {
        Map.Entry<DevEnchantment, EnchantmentData> entry = entries.get(index);
        if (!entry.getKey().equals(enchantment)) continue;
        int remaining = entry.getValue().level() - excess;
        if (remaining > 0) {
          entries.set(index, Map.entry(enchantment, new EnchantmentData(remaining, entry.getValue().metadata())));
          excess = 0;
        } else {
          excess -= entry.getValue().level();
          entries.remove(index);
        }
      }
    }
  }

  static void appendUnstoredEnchantments(
    List<Map.Entry<DevEnchantment, EnchantmentData>> stored,
    Map<DevEnchantment, EnchantmentData> actual
  ) {
    var represented = stored.stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
    actual.entrySet().stream()
      .filter(entry -> !represented.contains(entry.getKey()))
      .filter(entry -> EnchantmentCatalog.offered(entry.getKey()))
      .forEach(stored::add);
  }

  static void collapseNonRepeatableEnchantments(
    List<Map.Entry<DevEnchantment, EnchantmentData>> entries
  ) {
    Map<EnchantmentId, Integer> firstById = new HashMap<>();
    for (int index = 0; index < entries.size(); index++) {
      Map.Entry<DevEnchantment, EnchantmentData> entry = entries.get(index);
      if (EnchantmentCatalog.repeatable(entry.getKey())) continue;
      Integer first = firstById.putIfAbsent(entry.getKey().id(), index);
      if (first == null) continue;
      Map.Entry<DevEnchantment, EnchantmentData> retained = entries.get(first);
      int combined = Math.min(3999, retained.getValue().level() + entry.getValue().level());
      entries.set(first, Map.entry(retained.getKey(),
        new EnchantmentData(combined, retained.getValue().metadata())));
      entries.remove(index--);
    }
  }

  private List<Map.Entry<DevEnchantment, EnchantmentData>> readSocketEntries(DevItemStack stack) {
    ItemMeta meta = stack.bukkitStack().getItemMeta();
    if (meta == null) return null;
    String raw = meta.getPersistentDataContainer().get(SOCKET_ENTRIES_KEY, PersistentDataType.STRING);
    if (raw == null) return null;
    ArrayList<Map.Entry<DevEnchantment, EnchantmentData>> entries = new ArrayList<>();
    if (raw.isBlank()) return entries;
    try {
      for (String value : raw.split(";")) {
        String[] parts = value.split("\\|", -1);
        if (parts.length != 3) continue;
        int index = Integer.parseInt(parts[0]);
        int level = Integer.parseInt(parts[2]);
        DevEnchantment enchantment = ItemsPlugin.instance().enchantments().get(EnchantmentId.parse(parts[1])).orElse(null);
        EnchantmentCategory category = enchantment == null ? null : EnchantmentCatalog.category(enchantment);
        if (category != null && level > 0) {
          int socketCategory = index >= 0 && index < sockets.size()
            ? sockets.get(index).category().ordinal() : category.ordinal();
          entries.add(Map.entry(enchantment,
            new EnchantmentData(level, Map.of(SLOT_KEY, index, CATEGORY_KEY, socketCategory))));
        }
      }
    } catch (RuntimeException ignored) {
      return null;
    }
    return entries;
  }

  private void writeSocketEntries(DevItemStack stack, Map<Integer, ? extends Map.Entry<DevEnchantment, EnchantmentData>> entries) {
    StringBuilder raw = new StringBuilder();
    entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
      if (raw.length() > 0) raw.append(';');
      raw.append(entry.getKey()).append('|').append(entry.getValue().getKey().id()).append('|').append(entry.getValue().getValue().level());
    });
    ItemMeta meta = stack.bukkitStack().getItemMeta();
    if (meta == null) return;
    meta.getPersistentDataContainer().set(SOCKET_ENTRIES_KEY, PersistentDataType.STRING, raw.toString());
    stack.bukkitStack().setItemMeta(meta);
  }

  private void synchronizeTotals(DevItemStack stack, Map<Integer, ? extends Map.Entry<DevEnchantment, EnchantmentData>> entries) {
    Map<DevEnchantment, Integer> totals = aggregateLevels(entries.values());
    stack.enchantmentData().keySet().stream().filter(EnchantmentCatalog::offered).filter(enchantment -> !totals.containsKey(enchantment))
      .forEach(stack::removeEnchantment);
    totals.forEach(stack::enchant);
  }

  static Map<DevEnchantment, Integer> aggregateLevels(
    Iterable<? extends Map.Entry<DevEnchantment, EnchantmentData>> entries
  ) {
    Map<DevEnchantment, Integer> totals = new HashMap<>();
    entries.forEach(entry -> totals.merge(entry.getKey(), entry.getValue().level(), Integer::sum));
    return Map.copyOf(totals);
  }

  static void separateFilledSocket(List<String> lines) {
    if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) lines.add("");
  }

  static void finishFilledSocket(List<String> lines, boolean hasSocketBelow) {
    if (hasSocketBelow && !lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) lines.add("");
  }

  private int firstFree(Map<Integer, ?> assigned, DevEnchantment enchantment) {
    return sockets.stream().filter(slot -> slot.accepts(enchantment) && !assigned.containsKey(slot.index()))
      .mapToInt(EnchantmentSlot::index).findFirst().orElse(-1);
  }

  private boolean acceptsOnItem(DevEnchantment enchantment) {
    boolean universal = sockets.stream().anyMatch(slot -> slot.category() == EnchantmentCategory.UNIVERSAL);
    return sockets.stream().anyMatch(slot -> slot.accepts(enchantment))
      && (universal || EnchantmentCatalog.applicable(enchantment, material()));
  }
  private static String locale(Player viewer, String key, Object... arguments) {
    return viewer == null ? Locale.get("en_us", key, arguments) : Locale.get(viewer, key, arguments);
  }
}
