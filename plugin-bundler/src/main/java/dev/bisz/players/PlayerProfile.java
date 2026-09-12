/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.players;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record PlayerProfile(
  UUID playerId,
  String currentName,
  String displayName,
  long permissionLevel,
  long experienceLevel,
  String language,
  Instant subscriptionExpiresAt,
  Map<String, Object> metadata
) {
  public PlayerProfile {
    metadata = Collections.unmodifiableMap(
      new LinkedHashMap<String, Object>(metadata)
    );
  }

  public Object getMetadata(String key) {
    return this.metadata.get(key);
  }

  public Object getMetadata(String key, Object defaultValue) {
    return this.metadata.getOrDefault(key, defaultValue);
  }
}
