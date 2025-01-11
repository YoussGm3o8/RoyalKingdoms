package com.roki.core;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;

public class FactionEventListener implements Listener {
    private final RoyalKingdomsCore plugin;

    public FactionEventListener(RoyalKingdomsCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // // Load player data from the database
        // PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        
        // // Update last login time
        // playerData.updateLastLogin();
        
        // Get faction from PlayerData
        Faction faction = plugin.getPlayerFaction(player);

        // Create or update the player's scoreboard
        plugin.getScoreboardManager().createScoreboard(player);

        // Send a welcome message
        if (faction != null) {
            player.sendMessage("§7Welcome back to the " + faction.getName() + " §7faction!");
        } else {
            player.sendMessage("§7Welcome to the Royal Kingdoms MCBE Vanilla Factions server! Create a faction with /f create <name>, or join an existing faction with /f join <name> by getting an invite from another player.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Player player = event.getPlayer();
        
        // // Save player data to the database when they leave
        // PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
        // PlayerData playerData = playerDataManager.getPlayerData(player);
        // playerData.savePlayerData(); // Ensure player data is saved
        // playerDataManager.saveAndRemove(player);
        
        // // Ensure all data is saved before the player is removed
        // playerDataManager.saveAll();
    }
}