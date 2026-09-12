/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.players.moderation;

import java.time.Instant;
import java.util.UUID;

public record BanRecord(
  UUID banId,
  UUID playerId,
  String reasonKey,
  Instant expiresAt
) {}
