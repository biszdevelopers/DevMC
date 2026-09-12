/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.currency;

import dev.bisz.bundler.BundlerPlugin;
import dev.bisz.players.Profile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class CurrencyManager {

  private static final Pattern REASON_PATTERN = Pattern.compile(
    "[a-z0-9][a-z0-9._:-]*"
  );
  private static CurrencyManager instance;
  private final Map<String, Purse> purses = new ConcurrentHashMap<>();
  private final List<Transaction> transactions = new ArrayList<>();
  private final Object transactionLock = new Object();
  private final JavaPlugin plugin;
  private long sessionStartedAt = System.currentTimeMillis();
  private UUID sessionId = UUID.randomUUID();

  CurrencyManager(JavaPlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    instance = this;
  }

  public static CurrencyManager getInstance() {
    return instance;
  }

  public void loadPurse(String playerId, Purse purse) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(purse, "purse");
    Purse previous = purses.put(playerId, purse);
    Purse before = previous == null ? new Purse(0L) : previous;
    if (before.amount() != purse.amount()) {
      try {
        this.fireUpdate(
          UUID.fromString(playerId),
          before,
          purse.amount() - before.amount(),
          purse.amount()
        );
      } catch (IllegalArgumentException ignored) {
        // Non-UUID keys cannot identify a Bukkit player for this event.
      }
    }
  }

  public Purse getPurse(String playerId) {
    return purses.getOrDefault(playerId, new Purse(0L));
  }

  public Purse adjustPurse(
    UUID playerId,
    NitsOperation operation,
    long amount,
    String reason
  ) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(reason, "reason");
    if (amount <= 0L) {
      throw new IllegalArgumentException("Nits amount must be positive");
    }
    if (!REASON_PATTERN.matcher(reason).matches()) {
      throw new IllegalArgumentException(
        "Transaction reason must contain only lowercase letters, digits, '.', '_', ':', or '-'"
      );
    }
    String key = playerId.toString();
    AtomicReference<Purse> previous = new AtomicReference<>();
    Purse updatedPurse = purses.compute(key, (ignored, current) -> {
      Purse purse = (current == null) ? new Purse(0L) : current;
      previous.set(purse);
      return switch (operation) {
        case GIVE -> new Purse(Math.addExact(purse.amount(), amount));
        case REMOVE -> purse.remove(amount);
      };
    });

    long change = operation == NitsOperation.GIVE ? amount : -amount;
    Transaction transaction = new Transaction(
      System.currentTimeMillis(),
      playerId,
      operation,
      amount,
      change,
      previous.get().amount(),
      updatedPurse.amount(),
      reason
    );
    synchronized (transactionLock) {
      transactions.add(transaction);
    }
    Profile.setMetadata(
      playerId,
      "purse",
      Map.of("amount", updatedPurse.amount())
    );
    this.fireUpdate(playerId, previous.get(), change, updatedPurse.amount());

    return updatedPurse;
  }

  public List<Transaction> transactions() {
    synchronized (transactionLock) {
      return List.copyOf(transactions);
    }
  }

  public TransactionArchive archiveTransactions() {
    synchronized (transactionLock) {
      long endedAt = System.currentTimeMillis();
      UUID archivedSessionId = sessionId;
      long archivedStartedAt = sessionStartedAt;
      List<Map<String, Object>> entries = transactions
        .stream()
        .map(Transaction::toMap)
        .toList();
      String file =
        "transactions/currency/" +
        archivedStartedAt +
        "-" +
        archivedSessionId +
        ".json";
      BundlerPlugin.instance()
        .jsonDatabase()
        .saveDataFromDataBase(
          file,
          Map.of(
            "schema",
            1,
            "session_id",
            archivedSessionId.toString(),
            "started_at",
            archivedStartedAt,
            "ended_at",
            endedAt,
            "transactions",
            entries
          )
        );

      TransactionArchive result = new TransactionArchive(
        archivedSessionId,
        archivedStartedAt,
        endedAt,
        entries.size(),
        file
      );
      transactions.clear();
      sessionStartedAt = endedAt;
      sessionId = UUID.randomUUID();
      return result;
    }
  }

  public TransactionArchive flushTransactions() {
    return archiveTransactions();
  }

  private void fireUpdate(
    UUID playerId,
    Purse purse,
    long change,
    long finalAmount
  ) {
    Player player = Bukkit.getPlayer(playerId);
    if (player == null || !player.isOnline()) {
      return;
    }
    Runnable event = () ->
      Bukkit.getPluginManager().callEvent(
        new PurseUpdateEvent(purse, player, change, finalAmount)
      );
    if (Bukkit.isPrimaryThread()) {
      event.run();
    } else {
      Bukkit.getScheduler().runTask(plugin, event);
    }
  }

  public enum NitsOperation {
    GIVE,
    REMOVE;

    public static NitsOperation fromCommandArgument(String value) {
      return switch (value.toLowerCase(java.util.Locale.ROOT)) {
        case "give" -> GIVE;
        case "remove" -> REMOVE;
        default -> throw new IllegalArgumentException(
          "Unknown nits operation: " + value
        );
      };
    }
  }

  public record Purse(long amount) {
    public Purse {
      if (amount < 0L) {
        throw new IllegalArgumentException("Purse balance cannot be negative");
      }
    }

    public Purse remove(long amount) {
      if (amount > this.amount) {
        throw new IllegalArgumentException("Insufficient nits");
      }
      return new Purse(this.amount - amount);
    }
  }

  public record Transaction(
    long occurredAt,
    UUID playerId,
    NitsOperation operation,
    long amount,
    long change,
    long balanceBefore,
    long balanceAfter,
    String reason
  ) {
    public Transaction {
      Objects.requireNonNull(playerId, "playerId");
      Objects.requireNonNull(operation, "operation");
      Objects.requireNonNull(reason, "reason");
    }

    private Map<String, Object> toMap() {
      return Map.of(
        "occurred_at",
        occurredAt,
        "player_id",
        playerId.toString(),
        "operation",
        operation.name().toLowerCase(Locale.ROOT),
        "amount",
        amount,
        "change",
        change,
        "balance_before",
        balanceBefore,
        "balance_after",
        balanceAfter,
        "reason",
        reason
      );
    }
  }

  public record TransactionArchive(
    UUID sessionId,
    long startedAt,
    long endedAt,
    int transactionCount,
    String file
  ) {}
}
