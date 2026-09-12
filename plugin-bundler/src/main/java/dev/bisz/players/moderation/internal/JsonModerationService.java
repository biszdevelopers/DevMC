/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.players.moderation.internal;

import dev.bisz.players.moderation.BanRecord;
import dev.bisz.players.moderation.ModerationService;
import dev.bisz.storage.JsonDatabase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class JsonModerationService implements ModerationService {

  private final JsonDatabase database;

  public JsonModerationService(JsonDatabase database) {
    this.database = database;
  }

  @Override
  public CompletionStage<Optional<BanRecord>> activeBan(UUID playerId) {
    return this.database.supplyAsync(() -> {
        Map<String, Object> bans = this.database.loadDataFromDataBase(
          "banned.json"
        );
        Map<String, Object> ban = this.database.objectValue(
          bans.get(playerId.toString())
        );
        if (ban.isEmpty() || Boolean.TRUE.equals(ban.get("revoked"))) {
          return Optional.empty();
        }
        long expires = JsonModerationService.number(ban.get("expires"), -1L);
        if (expires >= 0L && expires <= System.currentTimeMillis()) {
          return Optional.empty();
        }
        String reason = this.reason(ban);
        UUID banId = JsonModerationService.uuid(
          ban.get("banId"),
          UUID.nameUUIDFromBytes(
            (String.valueOf(playerId) + ":" + reason + ":" + expires).getBytes(
              StandardCharsets.UTF_8
            )
          )
        );
        return Optional.of(
          new BanRecord(
            banId,
            playerId,
            reason,
            expires < 0L ? null : Instant.ofEpochMilli(expires)
          )
        );
      });
  }

  private String reason(Map<String, Object> ban) {
    String text;
    String text2;
    Object direct = ban.get("reasonKey");
    if (direct instanceof String && !(text2 = (String) direct).isBlank()) {
      return text2;
    }
    Map<String, Object> legacy = this.database.objectValue(ban.get("reason"));
    Object type = legacy.get("type");
    return type instanceof String && !(text = (String) type).isBlank()
      ? text
      : "general";
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

  private static UUID uuid(Object value, UUID fallback) {
    if (value instanceof String) {
      String text = (String) value;
      try {
        return UUID.fromString(text);
      } catch (IllegalArgumentException ignored) {
        return fallback;
      }
    }
    return fallback;
  }
}
