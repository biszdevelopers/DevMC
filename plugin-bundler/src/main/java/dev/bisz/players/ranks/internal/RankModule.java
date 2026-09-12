package dev.bisz.players.ranks.internal;

import dev.bisz.bundler.internal.BundlerModule;
import dev.bisz.bundler.internal.ModuleContext;
import dev.bisz.players.PlayerProfile;
import dev.bisz.players.PlayerRankUpdateEvent;
import dev.bisz.players.ProfileService;
import dev.bisz.players.Rank;
import dev.bisz.players.RankService;
import dev.bisz.players.locales.PlayerLocaleUpdateEvent;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Applies the persisted player rank to chat, player lists, operator state, and scoreboards.
 */
public final class RankModule implements BundlerModule, RankService, Listener {

  private static final String TEAM_PREFIX = "bundler_rank_";

  private JavaPlugin plugin;
  private ProfileService profiles;

  @Override
  public String id() {
    return "ranks";
  }

  @Override
  public Set<String> dependencies() {
    return Set.of("profiles");
  }

  @Override
  public void start(ModuleContext context) {
    this.plugin = context.plugin();
    this.profiles = context.services().require(ProfileService.class);
    context.services().register(RankService.class, this);
    Bukkit.getPluginManager().registerEvents(this, this.plugin);
    for (Player player : Bukkit.getOnlinePlayers()) {
      this.apply(player);
    }
    this.synchronizeAllBoards();
  }

  @Override
  public void stop() {
    for (Scoreboard scoreboard : this.scoreboards()) {
      for (Rank rank : Rank.values()) {
        Team team = scoreboard.getTeam(teamName(rank));
        if (team != null) {
          team.unregister();
        }
      }
    }
  }

  @Override
  public Rank rank(Player player) {
    Objects.requireNonNull(player, "player");
    return this.profiles.cached(player).map(this::rank).orElse(Rank.NONE);
  }

  @Override
  public Rank rank(PlayerProfile profile) {
    return Rank.fromId(
      Objects.requireNonNull(profile, "profile").permissionLevel()
    );
  }

  @Override
  public CompletionStage<PlayerProfile> setRank(UUID playerId, Rank rank) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(rank, "rank");
    Rank previous = this.profiles.cached(playerId)
      .map(this::rank)
      .orElse(Rank.NONE);
    return this.profiles.updateRank(playerId, rank.id()).thenApply(profile -> {
        this.plugin.getServer()
          .getScheduler()
          .runTask(this.plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
              this.apply(player);
              Bukkit.getPluginManager().callEvent(
                new PlayerRankUpdateEvent(player, previous, rank)
              );
            }
            this.synchronizeAllBoards();
          });
        return profile;
      });
  }

  @Override
  public void synchronize(Scoreboard scoreboard) {
    Objects.requireNonNull(scoreboard, "scoreboard");
    for (Rank rank : Rank.values()) {
      Team team = scoreboard.getTeam(teamName(rank));
      if (team == null) {
        team = scoreboard.registerNewTeam(teamName(rank));
      }
      team.setPrefix(rank.colorPrefix());
      team.setColor(rank.color());
      for (String entry : Set.copyOf(team.getEntries())) {
        team.removeEntry(entry);
      }
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      Rank rank = this.rank(player);
      Team team = scoreboard.getTeam(teamName(rank));
      if (team != null) {
        team.addEntry(player.getName());
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onProfileLoaded(PlayerLocaleUpdateEvent event) {
    if (
      event.cause() == PlayerLocaleUpdateEvent.Cause.PROFILE_LOADED &&
      event.player().isOnline()
    ) {
      this.apply(event.player());
      this.synchronizeAllBoards();
    }
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    this.synchronizeAllBoards();
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    for (Scoreboard scoreboard : this.scoreboards()) {
      for (Rank rank : Rank.values()) {
        Team team = scoreboard.getTeam(teamName(rank));
        if (team != null) {
          team.removeEntry(event.getPlayer().getName());
        }
      }
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onChat(AsyncPlayerChatEvent event) {
    Rank rank = this.rank(event.getPlayer());
    event.setFormat(rank.displayName() + " %1$s§7: §r%2$s");
  }

  private void apply(Player player) {
    Rank rank = this.rank(player);
    player.setPlayerListName(rank.displayName() + " " + player.getName());
    player.setOp(rank.grantsOperator());
  }

  private void synchronizeAllBoards() {
    for (Scoreboard scoreboard : this.scoreboards()) {
      this.synchronize(scoreboard);
    }
  }

  private Set<Scoreboard> scoreboards() {
    LinkedHashSet<Scoreboard> scoreboards = new LinkedHashSet<>();
    scoreboards.add(Bukkit.getScoreboardManager().getMainScoreboard());
    for (Player player : Bukkit.getOnlinePlayers()) {
      scoreboards.add(player.getScoreboard());
    }
    return scoreboards;
  }

  private static String teamName(Rank rank) {
    return TEAM_PREFIX + rank.id();
  }
}
