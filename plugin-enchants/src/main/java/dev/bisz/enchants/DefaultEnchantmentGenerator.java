package dev.bisz.enchants;

import dev.bisz.items.DevEnchantment;
import dev.bisz.items.DevItemStack;
import dev.bisz.items.EnchantmentData;
import dev.bisz.items.ItemsPlugin;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Built-in typed-socket enchanting offer generator. */
public final class DefaultEnchantmentGenerator implements EnchantmentGenerator {
  static final int MAX_BOOKSHELF_PERCENT = 400;

  @Override public List<EnchantmentOffer> generate(EnchantingGenerationContext context) {
    ItemStack item = context.item();
    boolean book = item.getType() == Material.BOOK || item.getType() == Material.ENCHANTED_BOOK;
    DevItemStack wrapped = ItemsPlugin.instance().factory().wrap(item);
    if (book && !wrapped.enchantments().isEmpty()) return List.of();
    if (!(wrapped.definition() instanceof SocketedVanillaItem definition)) return List.of();
    int selectedSocket = context.selectedSocket();
    if (!validFreeSocket(definition, wrapped, selectedSocket)) return List.of();
    int percent = context.modifiers().stream().filter(BookshelfModifierData.class::isInstance)
      .map(BookshelfModifierData.class::cast).mapToInt(BookshelfModifierData::extraChancePercent).sum();
    RandomGenerator random = context.random();
    List<EnchantmentSelection> selected = select(item.getType(), wrapped, definition, selectedSocket, percent, random);
    if (selected.isEmpty()) return List.of();
    EnchantingCosts.OfferCost cost = EnchantsPlugin.instance().enchantingCosts().roll(item.getType(), random);
    return orderOffers(List.of(new EnchantmentOffer(
      cost.experienceLevels(), cost.lapisLazuli(), 1.0, selected, List.of())));
  }

  static List<EnchantmentOffer> orderOffers(List<EnchantmentOffer> offers) {
    ArrayList<EnchantmentOffer> ordered = new ArrayList<>(offers);
    ordered.sort(Comparator.comparingInt(EnchantmentOffer::experienceLevels)
      .thenComparingInt(EnchantmentOffer::lapisLazuli));
    return List.copyOf(ordered);
  }

  static int extraRolls(int percentage, RandomGenerator random) {
    int capped = Math.max(0, Math.min(percentage, MAX_BOOKSHELF_PERCENT));
    int rolls = 0;
    for (int chance = capped; chance > 0; chance -= 100)
      if (random.nextInt(100) < Math.min(chance, 100)) rolls++;
    return rolls;
  }

  private static List<EnchantmentSelection> select(
    Material material, DevItemStack stack, SocketedVanillaItem definition,
    int selectedSocket, int bookshelfPercent, RandomGenerator random
  ) {
    EnchantmentSlot slot = definition.sockets().get(selectedSocket);
    // Candidate-first generation: enumerate everything that can still be placed
    // in this socket without breaking a rule, then pick one. The offer exists
    // whenever this list is non-empty.
    List<DevEnchantment> choices = EnchantmentCatalog.candidates(
      slot,
      material,
      stack.enchantmentData().keySet(),
      ItemsPlugin.instance().enchantments().values().stream()
        .sorted(Comparator.comparing(enchantment -> enchantment.id().toString())).toList());
    if (choices.isEmpty()) return List.of();
    DevEnchantment selected = choices.get(random.nextInt(choices.size()));
    Map<org.bukkit.NamespacedKey, Object> metadata = new java.util.LinkedHashMap<>();
    metadata.put(SocketedVanillaItem.slotKey(), slot.index());
    metadata.put(SocketedVanillaItem.categoryKey(), slot.category().ordinal());
    if (SocketedVanillaItem.isMending(selected))
      metadata.put(SocketedVanillaItem.mendingCountKey(), 0);
    int maxLevel = EnchantmentCatalog.maximumLevel(selected);
    int level = Math.min(maxLevel, 1 + random.nextInt(Math.max(1, maxLevel)) + extraRolls(bookshelfPercent, random));
    return List.of(new EnchantmentSelection(selected, new EnchantmentData(level, metadata)));
  }

  private static boolean validFreeSocket(SocketedVanillaItem definition, DevItemStack stack, int selectedSocket) {
    return selectedSocket >= 0 && selectedSocket < definition.sockets().size()
      && definition.freeSlots(stack).stream().anyMatch(slot -> slot.index() == selectedSocket);
  }

}
