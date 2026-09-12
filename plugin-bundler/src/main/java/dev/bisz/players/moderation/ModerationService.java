/*
 * Decompiled with CFR 0.152.
 */
package dev.bisz.players.moderation;

import dev.bisz.players.moderation.BanRecord;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ModerationService {
  public CompletionStage<Optional<BanRecord>> activeBan(UUID var1);
}
