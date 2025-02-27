package com.roki.core;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.item.Item;
import cn.nukkit.utils.TextFormat;

/**
 * Manages all combat-related functionality including combat tagging,
 * PvP restrictions, and combat cooldowns.
 */
public class CombatManager {
    private final RoyalKingdomsCore plugin;
    private final int combatTagDuration;
    private final boolean displayMessage;

    public CombatManager(RoyalKingdomsCore plugin) {
        this.plugin = plugin;
        this.combatTagDuration = plugin.getConfig().getInt("combat.tag_duration", 15);
        this.displayMessage = plugin.getConfig().getBoolean("combat.display_message", true);
    }

    /**
     * Checks if two players can damage each other based on faction relationships
     * 
     * @param attacker The attacking player
     * @param victim The player being attacked
     * @return true if PvP is allowed, false otherwise
     */
    public boolean canPvp(Player attacker, Player victim) {
        if (attacker == null || victim == null) return true;
        
        // Check if they are in the same faction
        Faction attackerFaction = plugin.getPlayerFaction(attacker);
        Faction victimFaction = plugin.getPlayerFaction(victim);
        
        if (attackerFaction != null && victimFaction != null) {
            // Same faction - no PvP
            if (attackerFaction.getName().equalsIgnoreCase(victimFaction.getName())) {
                attacker.sendMessage(TextFormat.RED + "You cannot attack members of your own faction!");
                return false;
            }
            
            // Check if they are allies
            if (attackerFaction.isAlliedWith(victimFaction.getName())) {
                attacker.sendMessage(TextFormat.RED + "You cannot attack members of allied factions!");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Handles placing a player in combat
     * 
     * @param event The damage event
     * @return true if the combat is valid, false if it should be cancelled
     */
    public boolean handleCombat(EntityDamageByEntityEvent event) {
        Entity attackerEntity = event.getDamager();
        Entity victimEntity = event.getEntity();
        
        // Make sure both entities are players
        if (!(attackerEntity instanceof Player) || !(victimEntity instanceof Player)) {
            return true;
        }
        
        Player attacker = (Player) attackerEntity;
        Player victim = (Player) victimEntity;
        
        // Check if PvP is allowed between these players
        if (!canPvp(attacker, victim)) {
            return false;
        }
        
        // Apply combat tag to both players
        tagPlayer(attacker, victim);
        tagPlayer(victim, attacker);
        
        return true;
    }
    
    /**
     * Tags a player as being in combat
     * 
     * @param player The player to tag
     * @param enemy The enemy that caused the combat
     */
    public void tagPlayer(Player player, Player enemy) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
        if (playerData == null) return;
        
        boolean wasInCombat = playerData.isInCombat();
        
        // Set combat tag
        playerData.setCombatTag(enemy, combatTagDuration);
        
        // Display combat message only if newly tagged
        if (!wasInCombat && displayMessage) {
            player.sendMessage(TextFormat.RED + "You are now in combat! Do not log out for " + 
                    combatTagDuration + " seconds or you will be punished!");
            
            // Switch to combat scoreboard if enabled
            if (playerData.isPvpScoreboardEnabled()) {
                plugin.getScoreboardManager().updatePvpScoreboard(player);
            } else {
                plugin.getScoreboardManager().updateScoreboard(player);
            }
        }
    }
    
    /**
     * Removes a player's combat tag
     * 
     * @param player The player to untag
     */
    public void untagPlayer(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
        if (playerData == null) return;
        
        if (playerData.isInCombat()) {
            playerData.removeCombatTag();
            
            // Inform the player
            if (displayMessage) {
                player.sendMessage(TextFormat.GREEN + "You are no longer in combat.");
            }
            
            // Update scoreboard
            plugin.getScoreboardManager().updateScoreboard(player);
        }
    }
    
    /**
     * Handles item consumption and applies appropriate cooldowns
     * 
     * @param player The player
     * @param item The item being consumed
     * @return true if the consumption is allowed, false if on cooldown
     */
    public boolean handleItemConsumption(Player player, Item item) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
        if (playerData == null) return true;
        
        // Golden Apple (ID: 322, damage: 0)
        if (item.getId() == 322 && item.getDamage() == 0) {
            if (!playerData.canUseGoldenApple()) {
                int remaining = playerData.getRemainingGoldenAppleCooldown();
                player.sendMessage(TextFormat.RED + "You cannot eat another Golden Apple for " + 
                        remaining + " seconds!");
                return false;
            }
            
            int cooldown = plugin.getConfig().getInt("cooldowns.golden_apple", 10);
            playerData.setGoldenAppleCooldown(cooldown);
            player.sendMessage(TextFormat.YELLOW + "Golden Apple consumed! Cooldown: " + cooldown + "s");
        }
        
        // Enchanted Golden Apple (ID: 322, damage: 1)
        if (item.getId() == 322 && item.getDamage() == 1) {
            if (!playerData.canUseEnchantedGoldenApple()) {
                int remaining = playerData.getRemainingEnchantedGoldenAppleCooldown();
                player.sendMessage(TextFormat.RED + "You cannot eat another Enchanted Golden Apple for " + 
                        remaining + " seconds!");
                return false;
            }
            
            int cooldown = plugin.getConfig().getInt("cooldowns.enchanted_golden_apple", 30);
            playerData.setEnchantedGoldenAppleCooldown(cooldown);
            player.sendMessage(TextFormat.GOLD + "Enchanted Golden Apple consumed! Cooldown: " + cooldown + "s");
        }
        
        return true;
    }
    
    /**
     * Checks whether a player can teleport (not in combat)
     * 
     * @param player The player
     * @return true if the player can teleport, false otherwise
     */
    public boolean canTeleport(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
        if (playerData == null) return true;
        
        if (playerData.isInCombat()) {
            int remaining = playerData.getRemainingCombatTime();
            player.sendMessage(TextFormat.RED + "You cannot teleport while in combat! Wait " + 
                    remaining + " more seconds.");
            return false;
        }
        
        return true;
    }
}