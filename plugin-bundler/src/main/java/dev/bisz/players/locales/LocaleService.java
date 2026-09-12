/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 */
package dev.bisz.players.locales;

import dev.bisz.players.locales.LocalizedAudience;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;

public interface LocaleService {
  public LocalizedAudience audience(Player var1);

  public String translate(LocalizedAudience var1, String var2, Object... var3);

  public String translate(String var1, String var2, Object... var3);

  public String minecraft(Player var1, String var2, Object... var3);

  public String minecraft(String var1, String var2, Object... var3);

  public CompletionStage<Void> changeLanguage(Player var1, String var2);

  public List<String> availableLanguages();

  /** Returns the number of editable server translations in a language. */
  public int translationCount(String language);

  /** Returns completion against the most complete editable language, from 0 through 100. */
  public double translationCoverage(String language);

  public void reload();
}
