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
        
        // Get PlayerData from manager
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        
        // Update last login time
        playerData.updateLastLogin();
        
        // Check if they're still eligible to be faction leader
        playerData.checkFactionLeaderStatus();
        
        // Get faction from PlayerData
        Faction faction = plugin.getPlayerFaction(player);

        // Create or update the player's scoreboard
        plugin.getScoreboardManager().createScoreboard(player);

        // Send a welcome message
        if (faction != null) {
            player.sendMessage("§7Welcome back to the " + faction.getColor() + " " + faction.getName() + " §7faction!");
        } else {
            player.sendMessage("§7Welcome to the Royal Kingdoms MCBE Vanilla Factions server! Join a faction with /f join");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Save player data when they leave
        PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
        playerDataManager.saveAndRemove(player);
    }
}