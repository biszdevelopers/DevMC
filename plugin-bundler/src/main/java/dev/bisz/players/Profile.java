package dev.bisz.players;

import dev.bisz.bundler.BundlerPlugin;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;

/** Legacy-style static access to Bundler player profiles. */
public final class Profile {

  private Profile() {}

  public static PlayerProfile findByOwner(Player player) {
    return service().cached(player).orElse(null);
  }

  public static CompletionStage<PlayerProfile> load(Player player) {
    return service().load(
      player.getUniqueId(),
      player.getName(),
      player.getDisplayName()
    );
  }

  public static CompletionStage<PlayerProfile> setMetadata(
    UUID playerId,
    String key,
    Object value
  ) {
    return service().setMetadata(playerId, key, value);
  }

  private static ProfileService service() {
    return BundlerPlugin.instance().profileService();
  }
}
