/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.bukkit.scoreboard.Criteria
 *  org.bukkit.scoreboard.DisplaySlot
 *  org.bukkit.scoreboard.Objective
 *  org.bukkit.scoreboard.Scoreboard
 */
package dev.bisz.scoreboards;

import dev.bisz.bundler.BundlerPlugin;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public abstract class DevScoreboard {

  protected final Player player;
  private final Scoreboard scoreboard;
  private final Objective objective;
  private final List<Line> lines = new ArrayList<Line>();
  private Scoreboard previousScoreboard;
  private boolean shown;
  private boolean disposed;

  protected DevScoreboard(Player player, String objectiveName, String title) {
    this.player = Objects.requireNonNull(player, "player");
    Objects.requireNonNull(objectiveName, "objectiveName");
    this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
    this.objective = this.scoreboard.registerNewObjective(
      objectiveName,
      Criteria.DUMMY,
      Objects.requireNonNull(title, "title")
    );
    this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
  }

  public final void show() {
    this.requireActive();
    if (!this.shown) {
      this.previousScoreboard = this.player.getScoreboard();
      this.shown = true;
    }
    BundlerPlugin.instance().rankService().synchronize(this.scoreboard);
    this.player.setScoreboard(this.scoreboard);
  }

  public final void setTitle(String title) {
    this.requireActive();
    this.objective.setDisplayName(Objects.requireNonNull(title, "title"));
  }

  public final void addLine(String line) {
    this.addLine(this.lines.size(), line);
  }

  public final void addLine(int index, String line) {
    this.requireActive();
    if (index < 0 || index > this.lines.size()) {
      throw new IndexOutOfBoundsException("Invalid line index: " + index);
    }
    this.lines.add(index, new Line(line, this.uniqueEntry(line, -1)));
    this.updateScores();
  }

  public final void setLine(int index, String line) {
    this.requireActive();
    if (index < 0 || index >= this.lines.size()) {
      throw new IndexOutOfBoundsException("Invalid line index: " + index);
    }
    Line current = this.lines.get(index);
    if (current.value().equals(line)) {
      return;
    }
    this.lines.set(index, new Line(line, this.uniqueEntry(line, index)));
    this.updateScores();
  }

  public final void removeLine(int index) {
    this.requireActive();
    if (index < 0 || index >= this.lines.size()) {
      throw new IndexOutOfBoundsException("Invalid line index: " + index);
    }
    this.lines.remove(index);
    this.updateScores();
  }

  public final void clearLines() {
    this.requireActive();
    this.lines.clear();
    this.updateScores();
  }

  public final List<String> lines() {
    return this.lines.stream().map(Line::value).toList();
  }

  public final boolean isDisposed() {
    return this.disposed;
  }

  public final void dispose() {
    if (this.disposed) {
      return;
    }
    this.disposed = true;
    try {
      this.onDispose();
    } finally {
      if (
        this.shown &&
        this.player.isOnline() &&
        this.player.getScoreboard() == this.scoreboard &&
        this.previousScoreboard != null
      ) {
        this.player.setScoreboard(this.previousScoreboard);
      }
      this.scoreboard.clearSlot(DisplaySlot.SIDEBAR);
      for (String entry : this.scoreboard.getEntries()) {
        this.scoreboard.resetScores(entry);
      }
      this.objective.unregister();
      this.lines.clear();
      this.previousScoreboard = null;
      this.shown = false;
    }
  }

  protected void onDispose() {}

  private void updateScores() {
    for (String entry : this.scoreboard.getEntries()) {
      this.scoreboard.resetScores(entry);
    }
    int score = this.lines.size();
    for (Line line : this.lines) {
      this.objective.getScore(line.entry()).setScore(score--);
    }
  }

  private String uniqueEntry(String value, int ignoredIndex) {
    String entry = Objects.requireNonNull(value, "line");
    HashSet<String> usedEntries = new HashSet<String>();
    for (int index = 0; index < this.lines.size(); ++index) {
      if (index == ignoredIndex) continue;
      usedEntries.add(this.lines.get(index).entry());
    }
    while (usedEntries.contains(entry)) {
      entry = entry + "\u00a7r";
    }
    return entry;
  }

  private void requireActive() {
    if (this.disposed) {
      throw new IllegalStateException("Scoreboard has been disposed");
    }
  }

  private record Line(String value, String entry) {}
}
