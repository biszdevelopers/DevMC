/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.bisz.bundler.BundlerPlugin
 *  dev.bisz.players.PlayerProfile
 *  dev.bisz.players.ProfileService
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.plugin.java.JavaPlugin
 */
package dev.bisz.currency;

import dev.bisz.currency.CurrencyManager;
import dev.bisz.players.PlayerProfile;
import dev.bisz.players.Profile;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class CurrencyManagerListener
implements Listener {
    private static final String PURSE_METADATA_KEY = "purse";
    private static final CurrencyManager.Purse DEFAULT_PURSE = new CurrencyManager.Purse(0L);
    private final JavaPlugin plugin;
    private final CurrencyManager currencyManager;

    public CurrencyManagerListener(JavaPlugin plugin, CurrencyManager currencyManager) {
        this.plugin = plugin;
        this.currencyManager = currencyManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        Profile.load(player).thenCompose(profile -> {
            if (profile.getMetadata(PURSE_METADATA_KEY) != null) {
                return CompletableFuture.completedFuture(profile);
            }
            return Profile.setMetadata(playerId, PURSE_METADATA_KEY, Map.of("amount", DEFAULT_PURSE.amount()));
        }).thenAccept(this::loadPurse).exceptionally(error -> {
            this.plugin.getLogger().warning("Cannot load purse for " + playerName + ": " + error.getMessage());
            return null;
        });
    }

    private void loadPurse(PlayerProfile profile) {
        this.currencyManager.loadPurse(profile.playerId().toString(), CurrencyManagerListener.purseFrom(profile.getMetadata(PURSE_METADATA_KEY)));
    }

    private static CurrencyManager.Purse purseFrom(Object metadata) {
        Map purse;
        Object amount;
        if (metadata instanceof Map && (amount = (purse = (Map)metadata).get("amount")) instanceof Number) {
            Number number = (Number)amount;
            return new CurrencyManager.Purse(number.longValue());
        }
        return DEFAULT_PURSE;
    }
}
