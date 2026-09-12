/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.players.web;

import java.net.InetAddress;
import java.util.UUID;

public record LoginAttempt(
  UUID playerId,
  String playerName,
  InetAddress address
) {}
