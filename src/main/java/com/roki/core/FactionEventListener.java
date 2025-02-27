package com.roki.core;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.player.PlayerDeathEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.event.player.PlayerItemConsumeEvent;
import cn.nukkit.form.response.FormResponseSimple;
import cn.nukkit.form.response.FormResponseCustom;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.item.Item;
import cn.nukkit.utils.TextFormat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public class FactionEventListener implements Listener {
    private final RoyalKingdomsCore plugin;
    private final Set<Integer> processedResponses = new HashSet<>();

    public FactionEventListener(RoyalKingdomsCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Load or create player data
        String uuid = player.getUniqueId().toString();
        PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
        PlayerData playerData = playerDataManager.getPlayerData(uuid);
        
        if (playerData == null) {
            // Create new player data
            playerData = new PlayerData(uuid, player.getName(), null, "Member", null, Instant.now(), 0);
            playerDataManager.addPlayerData(playerData);
        } else {
            // Update last login time
            playerData.setLastLogin(Instant.now());
        }
        
        // Get faction from PlayerData
        Faction faction = plugin.getPlayerFaction(player);

        // Create or update the player's scoreboard if enabled in config
        if (plugin.getConfig().getBoolean("scoreboard.enable_on_join", true)) {
            plugin.getScoreboardManager().createScoreboard(player);
        }

        // Send a welcome message
        if (faction != null) {
            player.sendMessage(TextFormat.GRAY + "Welcome back to the " + faction.getName() + TextFormat.GRAY + " faction!");
        } else {
            player.sendMessage(TextFormat.GRAY + "Welcome to the Royal Kingdoms MCBE Vanilla Factions server! Create a faction with /f create <name>, or join an existing faction with /f join <name> by getting an invite from another player.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is combat tagged
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
        if (playerData != null && playerData.isInCombat()) {
            // Combat logging penalty
            player.kill(); // Kill the player
            plugin.getServer().broadcastMessage(TextFormat.RED + player.getName() + " combat logged and was punished!");
        }
        
        // Remove scoreboard
        plugin.getScoreboardManager().removeScoreboard(player);
        plugin.getScoreboardManager().removePvpScoreboard(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Handle combat between players
        if (!plugin.getCombatManager().handleCombat(event)) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && !event.isCancelled()) {
            Player player = (Player) event.getEntity();
            
            // Update PVP scoreboard if player has it enabled
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
            if (playerData != null && playerData.isPvpScoreboardEnabled()) {
                plugin.getScoreboardManager().updatePvpScoreboard(player);
            }
        }
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        plugin.getFactionShieldManager().onEntityExplode(event);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Don't process if the event is cancelled already
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        
        // Check if player changed block position (not just looking around)
        if (event.getFrom().getFloorX() == event.getTo().getFloorX() && 
            event.getFrom().getFloorY() == event.getTo().getFloorY() && 
            event.getFrom().getFloorZ() == event.getTo().getFloorZ()) {
            return; // Just looking around, not moving position
        }
        
        // Handle teleportation cancellation on movement
        plugin.getTeleportManager().handlePlayerMove(player);
        
        // Handle faction territory notifications
        try {
            plugin.getFactionShieldManager().onPlayerMove(event);
        } catch (Exception e) {
            plugin.getLogger().error("Error handling PlayerMoveEvent in FactionShieldManager", e);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player != null) {
            // Remove combat tag on death
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
            if (playerData != null) {
                playerData.removeCombatTag();
            }
            
            // Credit kill to killer faction if applicable
            Entity killerEntity = player.getKiller();
            if (killerEntity instanceof Player) {
                Player killer = (Player) killerEntity;
                Faction killerFaction = plugin.getPlayerFaction(killer);
                if (killerFaction != null) {
                    killerFaction.incrementKills();
                }
            }
            
            // Handle default death behavior
            handlePlayerDeath(event);
        }
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        plugin.getCommandController().handlePlayerChat(player, message);
        event.setCancelled(true);
    }
    
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        Item item = event.getItem();
        
        // Handle golden apple cooldowns
        if (!plugin.getCombatManager().handleItemConsumption(player, item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerFormResponded(PlayerFormRespondedEvent event) {
        if (event.getWindow() instanceof FormWindowSimple) {
            FormWindowSimple window = (FormWindowSimple) event.getWindow();
            FormResponseSimple response = (FormResponseSimple) event.getResponse();
            if (response == null || processedResponses.contains(response.hashCode())) {
                plugin.handleFormClose(event.getPlayer(), window);
                return;
            }
            processedResponses.add(response.hashCode());
            
            if (window.getTitle().equals("Faction Management")) {
                plugin.getCommandController().handleGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Faction Invites")) {
                plugin.getCommandController().handleInviteGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Leader Tools")) {
                plugin.getCommandController().handleLeaderGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Faction List")) {
                plugin.getCommandController().handleFactionListGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Invite Player")) {
                plugin.getCommandController().handleInvitePlayerGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Promote Member")) {
                plugin.getCommandController().handlePromoteDemoteGuiResponse(event.getPlayer(), response, "promote");
            } else if (window.getTitle().equals("Demote Member")) {
                plugin.getCommandController().handlePromoteDemoteGuiResponse(event.getPlayer(), response, "demote");
            } else if (window.getTitle().equals("Manage Alliances")) {
                plugin.getCommandController().handleAllianceManagementGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().startsWith("Alliance Actions: ")) {
                if (window.getTitle().length() > "Alliance Actions: ".length()) {
                    String targetFaction = window.getTitle().substring("Alliance Actions: ".length());
                    plugin.getCommandController().handleAllianceActionsGuiResponse(event.getPlayer(), response, targetFaction);
                }
            } else if (window.getTitle().equals("Leave Faction")) {
                plugin.getCommandController().handleLeaveFactionConfirmationResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Disband Faction")) {
                plugin.getCommandController().handleDisbandFactionConfirmationResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Claim Land")) {
                plugin.getFactionShieldManager().handleShieldGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Manage Permissions")) {
                plugin.getFactionShieldManager().handlePermissionGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Admin Faction Management")) {
                plugin.getCommandController().handleAdminGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().startsWith("Manage Faction: ")) {
                if (window.getTitle().length() > "Manage Faction: ".length()) {
                    String factionName = window.getTitle().substring("Manage Faction: ".length());
                    plugin.getCommandController().handleFactionAdminOptionsResponse(event.getPlayer(), response, factionName);
                }
            }
        } else if (event.getWindow() instanceof FormWindowCustom) {
            FormWindowCustom window = (FormWindowCustom) event.getWindow();
            FormResponseCustom response = (FormResponseCustom) event.getResponse();
            if (response == null || processedResponses.contains(response.hashCode())) {
                plugin.handleFormClose(event.getPlayer(), window);
                return;
            }
            processedResponses.add(response.hashCode());
            
            if (window.getTitle().equals("Create Faction")) {
                plugin.getCommandController().handleCreateFactionFormResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Deposit Money")) {
                plugin.getCommandController().handleDepositMoneyFormResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Member Permissions")) {
                plugin.getFactionShieldManager().handleMemberPermissionGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Ally Permissions")) {
                plugin.getFactionShieldManager().handleAllyPermissionGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().startsWith("Modify Vault Balance: ")) {
                if (window.getTitle().length() > "Modify Vault Balance: ".length()) {
                    String factionName = window.getTitle().substring("Modify Vault Balance: ".length());
                    plugin.getCommandController().handleModifyVaultBalanceFormResponse(event.getPlayer(), response, factionName);
                }
            } else if (window.getTitle().startsWith("Modify Kills: ")) {
                if (window.getTitle().length() > "Modify Kills: ".length()) {
                    String factionName = window.getTitle().substring("Modify Kills: ".length());
                    plugin.getCommandController().handleModifyKillsFormResponse(event.getPlayer(), response, factionName);
                }
            } else if (window.getTitle().startsWith("Change Leader: ")) {
                if (window.getTitle().length() > "Change Leader: ".length()) {
                    String factionName = window.getTitle().substring("Change Leader: ".length());
                    plugin.getCommandController().handleChangeLeaderFormResponse(event.getPlayer(), response, factionName);
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        plugin.getFactionShieldManager().onBlockBreak(event);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        plugin.getFactionShieldManager().onBlockPlace(event);
    }

    private void handlePlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player != null) {
            // Custom respawn location - try spawn first, then default world spawn
            try {
                Location spawnLocation = plugin.getTeleportManager().getSpawnLocation();
                if (spawnLocation != null) {
                    player.teleport(spawnLocation);
                    player.sendMessage(TextFormat.GREEN + "You have been teleported to spawn.");
                } else {
                    Level defaultLevel = plugin.getServer().getDefaultLevel();
                    if (defaultLevel != null) {
                        player.teleport(defaultLevel.getSpawnLocation());
                        player.sendMessage(TextFormat.GREEN + "You have been teleported to the world spawn.");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().error("Error teleporting player after death", e);
                Level defaultLevel = plugin.getServer().getDefaultLevel();
                if (defaultLevel != null) {
                    player.teleport(defaultLevel.getSpawnLocation());
                }
            }
        }
    }
}