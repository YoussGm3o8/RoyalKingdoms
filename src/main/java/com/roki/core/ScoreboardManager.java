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
    private final Map<Player, Scoreboard> pvpScoreboards = new HashMap<>();
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
    
    // Create and show a PVP scoreboard for a player
    public void createPvpScoreboard(Player player) {
        // Remove existing PVP scoreboard if present
        removePvpScoreboard(player);
        
        // Create a new scoreboard with the required parameters
        Scoreboard scoreboard = new Scoreboard("§4PVP §8Mode", SortOrder.DESCENDING, DisplaySlot.SIDEBAR);
        updatePvpScores(player, scoreboard);
        
        // Show the scoreboard to the player
        scoreboard.showTo(player);
        
        // Save the scoreboard for future updates
        pvpScoreboards.put(player, scoreboard);
        
        // Update player data
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
        if (playerData != null) {
            playerData.setPvpScoreboardEnabled(true);
        }
    }

    // Update the scoreboard for a player
    public void updateScoreboard(Player player) {
        // Check if player has PVP scoreboard enabled
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
        if (playerData != null && playerData.isPvpScoreboardEnabled() && playerData.isInCombat()) {
            updatePvpScoreboard(player);
            return;
        }
        
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
    
    // Update the PVP scoreboard for a player
    public void updatePvpScoreboard(Player player) {
        Scoreboard scoreboard = pvpScoreboards.get(player);
        if (scoreboard == null) {
            createPvpScoreboard(player);
            return;
        }
        
        scoreboard.holdUpdates(); // Pause updates for efficiency
        scoreboard.clear(); // Remove all existing scores
        
        // Add updated scores
        updatePvpScores(player, scoreboard);
        
        scoreboard.unholdUpdates(); // Resume updates and send changes
    }

    // Update the scores for the player
    private void updateScores(Player player, Scoreboard scoreboard) {
        Faction faction = plugin.getPlayerFaction(player);
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());

        // Player name (top of the sidebar)
        scoreboard.setScore("§6" + player.getName(), 10);

        // Player's faction or "None"
        if (faction != null) {
            scoreboard.setScore("§7Faction: §f" + faction.getName(), 9);
        } else {
            scoreboard.setScore("§7Faction: §fNone", 9);
        }

        // Player's money (using EconomyAPI)
        double balance = EconomyAPI.getInstance().myMoney(player);
        scoreboard.setScore("§7Money: §6$" + balance, 8);

        // Player's ping
        scoreboard.setScore("§7Ping: §a" + player.getPing() + " ms", 7);
        
        // Combat status
        if (playerData != null && playerData.isInCombat()) {
            scoreboard.setScore("§4Combat: §c" + playerData.getRemainingCombatTime() + "s", 6);
        } else {
            scoreboard.setScore("§7Combat: §aSafe", 6);
        }
        
        // Cooldowns
        if (playerData != null) {
            int gappleCooldown = playerData.getRemainingGoldenAppleCooldown();
            if (gappleCooldown > 0) {
                scoreboard.setScore("§7Gapple: §c" + gappleCooldown + "s", 5);
            } else {
                scoreboard.setScore("§7Gapple: §aReady", 5);
            }
        }

        // Add separator line
        scoreboard.setScore("§7----------------", 4);
        
        // Online players
        scoreboard.setScore("§7Online: §f" + plugin.getServer().getOnlinePlayers().size(), 3);
        
        // TPS
        double tps = plugin.getServer().getTicksPerSecond();
        String tpsColor = tps >= 18 ? "§a" : (tps >= 15 ? "§6" : "§c");
        scoreboard.setScore("§7TPS: " + tpsColor + String.format("%.1f", tps), 2);
        
        // Server name
        scoreboard.setScore("§6Royal §4Kingdoms", 1);
    }
    
    // Update the PVP scores for the player
    private void updatePvpScores(Player player, Scoreboard scoreboard) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
        
        // Title
        scoreboard.setScore("§c§lCOMBAT MODE", 10);
        
        // Combat timer
        if (playerData != null && playerData.isInCombat()) {
            scoreboard.setScore("§4Combat Tag: §c" + playerData.getRemainingCombatTime() + "s", 9);
            
            // Show last attacker if available
            String attacker = playerData.getLastAttacker();
            if (attacker != null) {
                scoreboard.setScore("§4Enemy: §c" + attacker, 8);
                
                // Try to get enemy player
                Player enemy = plugin.getServer().getPlayer(attacker);
                if (enemy != null && plugin.getConfig().getBoolean("scoreboard.pvp.show_enemy_ping", true)) {
                    scoreboard.setScore("§4Enemy Ping: §c" + enemy.getPing() + " ms", 7);
                }
            }
        } else {
            scoreboard.setScore("§7Combat: §aSafe", 9);
            scoreboard.setScore("§7", 8); // Empty line
            scoreboard.setScore("§7", 7); // Empty line
        }
        
        // Add separator
        scoreboard.setScore("§4--------------", 6);
        
        // Player data
        scoreboard.setScore("§cHP: §f" + (int)player.getHealth() + "§8/§f" + (int)player.getMaxHealth(), 5);
        scoreboard.setScore("§cPing: §f" + player.getPing() + " ms", 4);
        
        // Cooldowns
        if (playerData != null && plugin.getConfig().getBoolean("scoreboard.pvp.show_cooldowns", true)) {
            int gappleCooldown = playerData.getRemainingGoldenAppleCooldown();
            if (gappleCooldown > 0) {
                scoreboard.setScore("§cGapple: §f" + gappleCooldown + "s", 3);
            } else {
                scoreboard.setScore("§cGapple: §aReady", 3);
            }
            
            int egappleCooldown = playerData.getRemainingEnchantedGoldenAppleCooldown();
            if (egappleCooldown > 0) {
                scoreboard.setScore("§cE-Gapple: §f" + egappleCooldown + "s", 2);
            } else {
                scoreboard.setScore("§cE-Gapple: §aReady", 2);
            }
        }
        
        // Server name
        scoreboard.setScore("§6Royal §4Kingdoms", 1);
    }
    
    // Toggle between normal and PVP scoreboard
    public void togglePvpScoreboard(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
        if (playerData == null) return;
        
        if (playerData.isPvpScoreboardEnabled()) {
            playerData.setPvpScoreboardEnabled(false);
            removePvpScoreboard(player);
            createScoreboard(player);
            player.sendMessage("§aPVP Scoreboard disabled");
        } else {
            playerData.setPvpScoreboardEnabled(true);
            removeScoreboard(player);
            createPvpScoreboard(player);
            player.sendMessage("§aPVP Scoreboard enabled");
        }
    }

    // Remove a player's scoreboard
    public void removeScoreboard(Player player) {
        Scoreboard scoreboard = playerScoreboards.remove(player);
        if (scoreboard != null) {
            scoreboard.hideFor(player); // Hide the scoreboard for the player
        }
    }
    
    // Remove a player's PVP scoreboard
    public void removePvpScoreboard(Player player) {
        Scoreboard scoreboard = pvpScoreboards.remove(player);
        if (scoreboard != null) {
            scoreboard.hideFor(player); // Hide the scoreboard for the player
        }
        
        // Update player data
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
        if (playerData != null) {
            playerData.setPvpScoreboardEnabled(false);
        }
    }
}