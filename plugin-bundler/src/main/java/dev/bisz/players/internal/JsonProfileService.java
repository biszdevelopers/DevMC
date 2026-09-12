/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 */
package dev.bisz.players.internal;

import dev.bisz.players.PlayerProfile;
import dev.bisz.players.ProfileService;
import dev.bisz.storage.JsonDatabase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class JsonProfileService implements ProfileService {

  private static final String FILE_NAME = "players.json";
  private static final Set<String> STANDARD_FIELDS = Set.of(
    "name",
    "displayName",
    "nameHistory",
    "rank",
    "level",
    "lang",
    "subscription"
  );
  private final JsonDatabase database;
  private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<
    UUID,
    PlayerProfile
  >();

  public JsonProfileService(JsonDatabase database) {
    this.database = database;
  }

  @Override
  public CompletionStage<PlayerProfile> load(
    UUID playerId,
    String playerName,
    String displayName
  ) {
    return this.database.supplyAsync(() ->
      this.database.updateObject(FILE_NAME, players ->
        this.loadAndUpdate(
          (Map<String, Object>) players,
          playerId,
          playerName,
          displayName
        )
      )
    );
  }

  @Override
  public Optional<PlayerProfile> cached(UUID playerId) {
    return Optional.ofNullable(this.cache.get(playerId));
  }

  @Override
  public Optional<PlayerProfile> cached(Player player) {
    return this.cached(player.getUniqueId());
  }

  @Override
  public CompletionStage<PlayerProfile> updateLanguage(
    UUID playerId,
    String language
  ) {
    return this.database.supplyAsync(() ->
      this.database.updateObject(FILE_NAME, players ->
        this.updateStoredLanguage(
          (Map<String, Object>) players,
          playerId,
          language
        )
      )
    );
  }

  @Override
  public CompletionStage<PlayerProfile> updateRank(UUID playerId, long rank) {
    return this.database.supplyAsync(() ->
      this.database.updateObject(FILE_NAME, players ->
        this.updateStoredRank((Map<String, Object>) players, playerId, rank)
      )
    );
  }

  @Override
  public CompletionStage<PlayerProfile> setMetadata(
    UUID playerId,
    String key,
    Object value
  ) {
    JsonProfileService.validateMetadataKey(key);
    return this.database.supplyAsync(() ->
      this.database.updateObject(FILE_NAME, players ->
        this.updateStoredMetadata(
          (Map<String, Object>) players,
          playerId,
          key,
          value
        )
      )
    );
  }

  @Override
  public CompletionStage<Collection<PlayerProfile>> allProfiles() {
    return database.supplyAsync(() -> {
      List<PlayerProfile> result = new ArrayList<>();
      Map<String, Object> players = database.loadDataFromDataBase(FILE_NAME);
      for (var entry : players.entrySet())
        try {
          UUID id = UUID.fromString(entry.getKey());
          result.add(
            profileFromStored(id, database.objectValue(entry.getValue()))
          );
        } catch (IllegalArgumentException ignored) {}
      result.sort(
        java.util.Comparator.comparing(
          PlayerProfile::currentName,
          String.CASE_INSENSITIVE_ORDER
        )
      );
      return List.copyOf(result);
    });
  }

  @Override
  public CompletionStage<Optional<PlayerProfile>> findByName(String name) {
    String query = name.strip();
    return database.supplyAsync(() -> {
      Map<String, Object> players = database.loadDataFromDataBase(FILE_NAME);
      for (var entry : players.entrySet()) {
        Map<String, Object> stored = database.objectValue(entry.getValue());
        boolean matches =
          query.equalsIgnoreCase(text(stored.get("name"), "")) ||
          stringList(stored.get("nameHistory"))
            .stream()
            .anyMatch(query::equalsIgnoreCase);
        if (matches) try {
          return Optional.of(
            profileFromStored(UUID.fromString(entry.getKey()), stored)
          );
        } catch (IllegalArgumentException ignored) {}
      }
      return Optional.empty();
    });
  }

  private PlayerProfile loadAndUpdate(
    Map<String, Object> players,
    UUID playerId,
    String playerName,
    String displayName
  ) {
    String id = playerId.toString();
    Map<String, Object> profile = this.database.objectValue(players.get(id));
    long permission = JsonProfileService.number(profile.get("rank"), 1L);
    long level = JsonProfileService.number(profile.get("level"), permission);
    String language = JsonProfileService.text(
      profile.get("lang"),
      "ZH_CN"
    ).toLowerCase();
    long subscription = JsonProfileService.number(
      profile.get("subscription"),
      -1L
    );
    List<String> history = JsonProfileService.stringList(
      profile.get("nameHistory")
    );
    if (!history.contains(playerName)) {
      history.add(playerName);
    }
    profile.put("name", playerName);
    profile.put("displayName", displayName);
    profile.put("nameHistory", history);
    profile.put("rank", permission);
    profile.put("level", level);
    profile.put("lang", language.toUpperCase());
    profile.put("subscription", subscription);
    players.put(id, profile);
    PlayerProfile result = JsonProfileService.profileFromStored(
      playerId,
      profile
    );
    this.cache.put(playerId, result);
    return result;
  }

  private PlayerProfile updateStoredLanguage(
    Map<String, Object> players,
    UUID playerId,
    String language
  ) {
    String id = playerId.toString();
    Map<String, Object> stored = this.database.objectValue(players.get(id));
    PlayerProfile current = this.cache.get(playerId);
    String name = current == null
      ? JsonProfileService.text(stored.get("name"), id)
      : current.currentName();
    String displayName = current == null
      ? JsonProfileService.text(stored.get("displayName"), name)
      : current.displayName();
    long permission = current == null
      ? JsonProfileService.number(stored.get("rank"), 1L)
      : current.permissionLevel();
    long level = current == null
      ? JsonProfileService.number(stored.get("level"), permission)
      : current.experienceLevel();
    long subscription = JsonProfileService.number(
      stored.get("subscription"),
      -1L
    );
    stored.put("name", name);
    stored.put("displayName", displayName);
    stored.put("rank", permission);
    stored.put("level", level);
    stored.put("lang", language.toUpperCase());
    stored.put("subscription", subscription);
    players.put(id, stored);
    PlayerProfile updated = JsonProfileService.profileFromStored(
      playerId,
      stored
    );
    this.cache.put(playerId, updated);
    return updated;
  }

  private PlayerProfile updateStoredRank(
    Map<String, Object> players,
    UUID playerId,
    long rank
  ) {
    String id = playerId.toString();
    Map<String, Object> stored = this.database.objectValue(players.get(id));
    PlayerProfile current = this.cache.get(playerId);
    String name = current == null
      ? JsonProfileService.text(stored.get("name"), id)
      : current.currentName();
    String displayName = current == null
      ? JsonProfileService.text(stored.get("displayName"), name)
      : current.displayName();
    long level = current == null
      ? JsonProfileService.number(stored.get("level"), rank)
      : current.experienceLevel();
    String language = current == null
      ? JsonProfileService.text(stored.get("lang"), "ZH_CN")
      : current.language();
    long subscription = JsonProfileService.number(
      stored.get("subscription"),
      -1L
    );
    stored.put("name", name);
    stored.put("displayName", displayName);
    stored.put("rank", rank);
    stored.put("level", level);
    stored.put("lang", language.toUpperCase(java.util.Locale.ROOT));
    stored.put("subscription", subscription);
    players.put(id, stored);
    PlayerProfile updated = JsonProfileService.profileFromStored(
      playerId,
      stored
    );
    this.cache.put(playerId, updated);
    return updated;
  }

  private PlayerProfile updateStoredMetadata(
    Map<String, Object> players,
    UUID playerId,
    String key,
    Object value
  ) {
    String id = playerId.toString();
    Map<String, Object> stored = this.database.objectValue(players.get(id));
    if (value == null) {
      stored.remove(key);
    } else {
      stored.put(key, value);
    }
    players.put(id, stored);
    PlayerProfile updated = JsonProfileService.profileFromStored(
      playerId,
      stored
    );
    this.cache.put(playerId, updated);
    return updated;
  }

  private static PlayerProfile profileFromStored(
    UUID playerId,
    Map<String, Object> stored
  ) {
    String id = playerId.toString();
    String name = JsonProfileService.text(stored.get("name"), id);
    String displayName = JsonProfileService.text(
      stored.get("displayName"),
      name
    );
    long permission = JsonProfileService.number(stored.get("rank"), 1L);
    long level = JsonProfileService.number(stored.get("level"), permission);
    String language = JsonProfileService.text(
      stored.get("lang"),
      "ZH_CN"
    ).toLowerCase();
    long subscription = JsonProfileService.number(
      stored.get("subscription"),
      -1L
    );
    LinkedHashMap<String, Object> metadata = new LinkedHashMap<
      String,
      Object
    >();
    for (Map.Entry<String, Object> entry : stored.entrySet()) {
      if (STANDARD_FIELDS.contains(entry.getKey())) continue;
      metadata.put(entry.getKey(), entry.getValue());
    }
    return new PlayerProfile(
      playerId,
      name,
      displayName,
      permission,
      level,
      language,
      subscription < 0L ? null : Instant.ofEpochMilli(subscription),
      Collections.unmodifiableMap(metadata)
    );
  }

  private static void validateMetadataKey(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException(
        "Profile metadata key cannot be blank"
      );
    }
    if (STANDARD_FIELDS.contains(key)) {
      throw new IllegalArgumentException(
        "Profile metadata key conflicts with the standard profile field " + key
      );
    }
  }

  private static long number(Object value, long fallback) {
    long l;
    if (value instanceof Number) {
      Number number = (Number) value;
      l = number.longValue();
    } else {
      l = fallback;
    }
    return l;
  }

  private static String text(Object value, String fallback) {
    String text;
    return value instanceof String && !(text = (String) value).isBlank()
      ? text
      : fallback;
  }

  private static List<String> stringList(Object value) {
    ArrayList<String> result = new ArrayList<String>();
    if (value instanceof Iterable) {
      Iterable values = (Iterable) value;
      for (Object entry : values) {
        String text;
        if (
          !(entry instanceof String) || result.contains(text = (String) entry)
        ) continue;
        result.add(text);
      }
    }
    return result;
  }
}
