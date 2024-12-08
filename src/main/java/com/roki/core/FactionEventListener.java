package com.roki.core;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerJoinEvent;

public class FactionEventListener implements Listener {
    private final RoyalKingdomsCore plugin;

    public FactionEventListener(RoyalKingdomsCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
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
}