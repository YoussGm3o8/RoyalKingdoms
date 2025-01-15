package com.roki.core;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.player.PlayerDeathEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.form.response.FormResponseSimple;
import cn.nukkit.form.response.FormResponseCustom;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.level.Level;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;

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

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        plugin.getFactionShieldManager().onEntityExplode(event);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        try {
            plugin.getFactionShieldManager().onPlayerMove(event);
        } catch (Exception e) {
            plugin.getLogger().error("Error handling PlayerMoveEvent", e);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        handlePlayerDeath(event);
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        plugin.getCommandController().handlePlayerChat(player, message);
        event.setCancelled(true);
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
            plugin.getLogger().info("FormWindowSimple Title: " + window.getTitle());
            plugin.getLogger().info("FormWindowSimple Response: " + response.getClickedButton().getText());
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
            plugin.getLogger().info("FormWindowCustom Title: " + window.getTitle());
            plugin.getLogger().info("FormWindowCustom Response: " + response.getInputResponse(0));
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
            Level lobby = plugin.getServer().getLevelByName("Lobby");
            if (lobby != null) {
                player.teleport(lobby.getSpawnLocation());
                player.sendMessage("§aYou have been teleported to the Lobby.");
            } else {
                player.sendMessage("§cLobby world not found.");
            }
        }
    }
}