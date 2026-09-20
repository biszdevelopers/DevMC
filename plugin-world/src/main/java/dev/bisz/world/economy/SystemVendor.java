package dev.bisz.world.economy;

import dev.bisz.world.config.WorldSettings;
import dev.bisz.world.integration.CurrencyGateway;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * The one-way system vendor. It buys anything except contraband and never
 * sells, bounded by a per-player daily sell quota.
 */
public final class SystemVendor {

  private final PriceTable prices;
  private final WorldSettings settings;
  private final CurrencyGateway currency;
  private final Map<UUID, Quota> quotas = new HashMap<>();

  public SystemVendor(
    PriceTable prices,
    WorldSettings settings,
    CurrencyGateway currency
  ) {
    this.prices = Objects.requireNonNull(prices, "prices");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.currency = Objects.requireNonNull(currency, "currency");
  }

  /** The outcome of a sell attempt. */
  public record SellResult(
    int itemsSold,
    long nitsPaid,
    int contrabandRefused,
    boolean quotaReached
  ) {}

  /** Sells the item in the player's main hand. */
  public SellResult sellHand(Player player) {
    ItemStack hand = player.getInventory().getItemInMainHand();
    if (hand == null || hand.getType().isAir()) {
      return new SellResult(0, 0L, 0, false);
    }
    return sell(player, hand);
  }

  /** Sells every sellable stack in the player's storage inventory. */
  public SellResult sellInventory(Player player) {
    PlayerInventory inventory = player.getInventory();
    int itemsSold = 0;
    long nitsPaid = 0L;
    int refused = 0;
    boolean quotaReached = false;
    for (int slot = 0; slot < inventory.getSize(); slot++) {
      ItemStack stack = inventory.getItem(slot);
      if (stack == null || stack.getType().isAir()) continue;
      if (Contraband.isContraband(stack)) {
        refused += stack.getAmount();
        continue;
      }
      long remaining = remainingQuota(player.getUniqueId());
      if (remaining <= 0L) {
        quotaReached = true;
        break;
      }
      long value = prices.stackValue(stack.getType(), stack.getAmount());
      if (value <= 0L) continue;
      long allowed = Math.min(value, remaining);
      int sellable = value <= remaining
        ? stack.getAmount()
        : (int) Math.max(1, allowed / Math.max(1L, prices.unitPrice(stack.getType())));
      if (sellable <= 0) {
        quotaReached = true;
        break;
      }
      ItemStack sold = stack.clone();
      sold.setAmount(sellable);
      long paid = prices.stackValue(sold.getType(), sellable);
      if (sellable >= stack.getAmount()) {
        inventory.setItem(slot, null);
      } else {
        ItemStack remainder = stack.clone();
        remainder.setAmount(stack.getAmount() - sellable);
        inventory.setItem(slot, remainder);
      }
      itemsSold += sellable;
      nitsPaid += paid;
      record(player.getUniqueId(), paid);
      if (paid < value) {
        quotaReached = true;
        break;
      }
    }
    if (nitsPaid > 0L) {
      currency.deposit(player.getUniqueId(), nitsPaid, "world.vendor_sale");
    }
    return new SellResult(itemsSold, nitsPaid, refused, quotaReached);
  }

  private SellResult sell(Player player, ItemStack stack) {
    if (Contraband.isContraband(stack)) {
      return new SellResult(0, 0L, stack.getAmount(), false);
    }
    long remaining = remainingQuota(player.getUniqueId());
    if (remaining <= 0L) {
      return new SellResult(0, 0L, 0, true);
    }
    long value = prices.stackValue(stack.getType(), stack.getAmount());
    long paid = Math.min(value, remaining);
    int sellable = value <= remaining
      ? stack.getAmount()
      : (int) Math.max(1, paid / Math.max(1L, prices.unitPrice(stack.getType())));
    if (sellable <= 0) {
      return new SellResult(0, 0L, 0, true);
    }
    paid = prices.stackValue(stack.getType(), sellable);
    ItemStack sold = stack.clone();
    sold.setAmount(sellable);
    if (sellable >= stack.getAmount()) {
      player.getInventory().setItemInMainHand(null);
    } else {
      stack.setAmount(stack.getAmount() - sellable);
      player.getInventory().setItemInMainHand(stack);
    }
    record(player.getUniqueId(), paid);
    if (paid > 0L) {
      currency.deposit(player.getUniqueId(), paid, "world.vendor_sale");
    }
    return new SellResult(sellable, paid, 0, value > remaining);
  }

  /** Nits the player may still earn today. */
  public long remainingQuota(UUID playerId) {
    Quota quota = quotas.get(playerId);
    long used = quota == null ? 0L : quota.used;
    return Math.max(0L, settings.dailySellQuota() - used);
  }

  private void record(UUID playerId, long paid) {
    Quota quota = quotas.computeIfAbsent(playerId, ignored -> new Quota());
    if (!quota.day.equals(LocalDate.now())) {
      quota.day = LocalDate.now();
      quota.used = 0L;
    }
    quota.used += paid;
  }

  private static final class Quota {

    private LocalDate day = LocalDate.now();
    private long used;
  }
}
