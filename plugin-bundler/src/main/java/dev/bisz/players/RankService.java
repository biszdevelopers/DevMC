package dev.bisz.players;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Resolves, persists, and applies player ranks.
 */
public interface RankService {
  Rank rank(Player player);

  Rank rank(PlayerProfile profile);

  CompletionStage<PlayerProfile> setRank(UUID playerId, Rank rank);

  void synchronize(Scoreboard scoreboard);
}
