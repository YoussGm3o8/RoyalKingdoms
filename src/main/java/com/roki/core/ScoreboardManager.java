package com.roki.core;

import cn.nukkit.Player;
import cn.nukkit.scoreboard.Scoreboard;
import cn.nukkit.scoreboard.Scoreboard.DisplaySlot;
import cn.nukkit.scoreboard.Scoreboard.SortOrder;

import me.onebone.economyapi.EconomyAPI;

import java.util.HashMap;
import java.util.Map;

public class ScoreboardManager {
    private final Map<Player, Scoreboard> playerScoreboards = new HashMap<>();
    private final RoyalKingdomsCore plugin;

    public ScoreboardManager(RoyalKingdomsCore plugin) {
        this.plugin = plugin;
    }

    // Create and show a scoreboard for a player
    public void createScoreboard(Player player) {
        // Remove existing scoreboard if present
        removeScoreboard(player);

        // Create a new scoreboard with the required parameters
        Scoreboard scoreboard = new Scoreboard("§6Royal §4Kingdoms", SortOrder.DESCENDING, DisplaySlot.SIDEBAR);
        updateScores(player, scoreboard);

        // Show the scoreboard to the player
        scoreboard.showTo(player);

        // Save the scoreboard for future updates
        playerScoreboards.put(player, scoreboard);
    }

    // Update the scoreboard for a player
    public void updateScoreboard(Player player) {
        Scoreboard scoreboard = playerScoreboards.get(player);
        if (scoreboard == null) {
            createScoreboard(player);
            return;
        }

        scoreboard.holdUpdates(); // Pause updates for efficiency
        scoreboard.clear(); // Remove all existing scores

        // Add updated scores
        updateScores(player, scoreboard);

        scoreboard.unholdUpdates(); // Resume updates and send changes
    }

    // Update the scores for the player
    private void updateScores(Player player, Scoreboard scoreboard) {
        Faction faction = plugin.getPlayerFaction(player);

        // Add hidden line separator at the top (to ensure proper alignment)
        scoreboard.setScore("§7---------------", 10);

        // Player name (top of the sidebar)
        scoreboard.setScore(player.getName(), 9);

        // Player's faction or "None"
        if (faction != null) {
            scoreboard.setScore(faction.getColor() + " " + faction.getName(), 8);
        } else {
            scoreboard.setScore(" ", 8);
        }

        // Player's money (using EconomyAPI)
        double balance = EconomyAPI.getInstance().myMoney(player);
        scoreboard.setScore("§7Money: §6$" + balance, 7);

        // Player's ping
        scoreboard.setScore("§7Ping: §7" + player.getPing() + " ms", 6);

        // Hide line numbers to avoid overlapping
        scoreboard.setScore(" ", 5);  // Empty space for hiding line
        
        // Add separator line
        scoreboard.setScore("§7---------------", 4);

        // Final line separator at the bottom
        scoreboard.setScore("§7", 3); // Invisible line to ensure proper space

        // Hidden lines can be used to push other lines to the correct positions
        scoreboard.setScore("§70.0.0.0", 2); // Empty line for padding
        scoreboard.setScore("§719132", 1); // Empty line for padding
    }

    // Remove a player's scoreboard
    public void removeScoreboard(Player player) {
        Scoreboard scoreboard = playerScoreboards.remove(player);
        if (scoreboard != null) {
            scoreboard.hideFor(player); // Hide the scoreboard for the player
        }
    }
}