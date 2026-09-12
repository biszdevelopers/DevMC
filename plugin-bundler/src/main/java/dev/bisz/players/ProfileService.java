/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 */
package dev.bisz.players;

import dev.bisz.players.PlayerProfile;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;

public interface ProfileService {
  public CompletionStage<PlayerProfile> load(
    UUID var1,
    String var2,
    String var3
  );

  public CompletionStage<PlayerProfile> updateLanguage(UUID var1, String var2);

  public CompletionStage<PlayerProfile> updateRank(UUID var1, long var2);

  public CompletionStage<PlayerProfile> setMetadata(
    UUID var1,
    String var2,
    Object var3
  );

  public Optional<PlayerProfile> cached(UUID var1);

  public Optional<PlayerProfile> cached(Player var1);
  /** Lists every durable profile without blocking the server thread. */
  CompletionStage<Collection<PlayerProfile>> allProfiles();
  /** Resolves current or historical names case-insensitively. */
  CompletionStage<Optional<PlayerProfile>> findByName(String name);
}
