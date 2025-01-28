package com.roki.core.commands;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.format.Chunk;
import cn.nukkit.network.protocol.GameRulesChangedPacket;
import cn.nukkit.network.protocol.TextPacket;
import cn.nukkit.level.particle.DustParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.scheduler.NukkitRunnable;

import com.roki.core.Faction;
import com.roki.core.FactionTabCompleter;
import com.roki.core.PlayerData;
import com.roki.core.PlayerDataManager;
import com.roki.core.RoyalKingdomsCore;
import com.roki.core.chunkProtection.ProtectedChunkData;
import com.roki.core.database.DatabaseManager;
import com.roki.core.database.DataModel;
import me.onebone.economyapi.EconomyAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.element.ElementInput;
import cn.nukkit.form.element.ElementToggle;
import cn.nukkit.form.response.FormResponseSimple;
import cn.nukkit.form.response.FormResponseCustom;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.player.PlayerTeleportEvent;
import cn.nukkit.block.Block;
import cn.nukkit.entity.item.EntityPrimedTNT;

import java.util.concurrent.ConcurrentHashMap;

public class FactionCommandController implements Listener {
    private final RoyalKingdomsCore plugin;
    private final DataModel dataModel;
    private final DatabaseManager db;
    private final Map<String, String> invites = new HashMap<>();
    private final Map<String, String> allyRequests = new HashMap<>();
    private Map<Player, Boolean> coordinatesEnabled = new HashMap<>();
    private Map<Player, ChatMode> chatModes = new HashMap<>();
    private List<Player> spyMode = new ArrayList<>();
    private final Map<String, List<ProtectedChunkData>> protectedChunks = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Boolean>> memberPermissions = new HashMap<>();
    private final Map<String, Map<String, Boolean>> allyPermissions = new HashMap<>();
    private final Set<Integer> processedResponses = new HashSet<>();
    private static final int CLAIM_COST = 16000;
    private static final int MAX_CHESTS = 2;
    private final Map<Player, Boolean> chunkBordersEnabled = new HashMap<>();

    public enum ChatMode {
        ALL, FACTION, ALLY
    }

    public FactionCommandController(RoyalKingdomsCore plugin) {
        this.plugin = plugin;
        this.dataModel = plugin.getDataModel();
        this.db = dataModel.getDatabaseManager();
    }

    public boolean handleFactionHomeCommand(Player player) {
        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        
        if (factionName == null) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        Location homeLocation = db.getWarp(factionName + "-base");
        if (homeLocation != null) {
            player.teleport(homeLocation);
            player.sendMessage("§aTeleported to " + factionName + " faction base.");
        } else {
            player.sendMessage("§cNo base location set for your faction.");
        }
        return true;
    }

    public boolean handleFactionInfoCommand(Player player, String[] args) {
        if (args.length == 1) {
            // Show info about player's current faction
            String factionName = db.getPlayerFaction(player.getUniqueId().toString());
            if (factionName != null) {
                Faction info = db.getFactionInfo(factionName);
                player.sendMessage("");
                player.sendMessage("§aYour faction: " + info.getName());
                player.sendMessage("§aLeader: §7" + info.getFactionLeader());
                sendFactionPlayersInfo(player, factionName);
                player.sendMessage("§aBalance: §7$" + info.getVaultBalance());
                player.sendMessage("§aKills: §7" + info.getKills());
            } else {
                player.sendMessage("§cYou are not in a faction.");
            }
            return true;
        } else if (args.length == 2) {
            String requestedFaction = args[1];
            Faction info = db.getFactionInfo(requestedFaction);
            
            if (info != null) {
                String factionName = db.getPlayerFaction(player.getUniqueId().toString());
                String color = "§c"; // Default to red for enemy factions
                if (requestedFaction.equalsIgnoreCase(factionName)) {
                    color = "§a"; // Green for own faction
                } else if (db.isAlly(factionName, requestedFaction)) {
                    color = "§e"; // Yellow for ally factions
                }
                player.sendMessage("");
                player.sendMessage(color + "Faction: " + info.getName());
                player.sendMessage(color +"Leader: §7" + info.getFactionLeader());
                sendFactionPlayersInfo(player, requestedFaction);
                player.sendMessage(color +"Balance: §7$" + info.getVaultBalance());
                player.sendMessage(color + "Kills: §7" + info.getKills());
            } else {
                player.sendMessage("§cFaction not found. Available factions:");
                for (String faction : db.getAllFactionNames()) {
                    player.sendMessage("§f- §7" + faction.toLowerCase());
                }
            }
            return true;
        }
        return false;
    }

    private void sendFactionPlayersInfo(Player player, String factionName) {
        List<String> players = db.getFactionPlayers(factionName);
        List<String> onlinePlayers = players.stream()
            .filter(name -> plugin.getServer().getPlayer(name) != null)
            .collect(Collectors.toList());
        List<String> offlinePlayers = players.stream()
            .filter(name -> plugin.getServer().getPlayer(name) == null)
            .collect(Collectors.toList());

        player.sendMessage("§7[" + onlinePlayers.size() + "/" + players.size() + "] "+ "§e" + String.join(", ", onlinePlayers) + "§7" +String.join(", ", offlinePlayers));
    }

    public boolean handleLeaveFactionCommand(Player player) {
        String currentFaction = db.getPlayerFaction(player.getUniqueId().toString());
        if (currentFaction != null) {
            db.removePlayerFromFaction(player.getUniqueId().toString());
            player.sendMessage("§aYou have left the " + currentFaction + " faction.");
        } else {
            player.sendMessage("§cYou are not in a faction.");
        }
        return true;
    }

    public boolean handleFactionDepositCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /f deposit <amount>");
            return true;
        }

        try {
            double amount = Double.parseDouble(args[1]);
            if (amount <= 0) {
                player.sendMessage("§cThe amount must be greater than 0.");
                return true;
            }

            String factionName = db.getPlayerFaction(player.getUniqueId().toString());
            if (factionName == null) {
                player.sendMessage("§cYou are not in a faction.");
                return true;
            }

            double playerBalance = EconomyAPI.getInstance().myMoney(player);
            if (playerBalance < amount) {
                player.sendMessage("§cYou do not have enough money to deposit.");
                return true;
            }

            db.addToFactionBalance(factionName, amount);
            EconomyAPI.getInstance().reduceMoney(player, amount);
            player.sendMessage("§aYou have deposited " + amount + " into the " + factionName + " faction's vault.");
            return true;

        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount.");
            return true;
        }
    }

    public boolean handleFactionMoneyCommand(Player player, String[] args) {
        if (args.length > 1) {
            String requestedFaction = args[1];
            Double balance = db.getFactionBalance(requestedFaction);
            if (balance != null) {
                player.sendMessage("§aFaction " + requestedFaction + "'s vault balance: §7$" + balance);
            } else {
                player.sendMessage("§cFaction not found.");
            }
            return true;
        }
        
        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        if (factionName != null) {
            Double balance = db.getFactionBalance(factionName);
            player.sendMessage("§aYour faction's vault balance: §7$" + balance);
        } else {
            player.sendMessage("§cYou are not in a faction.");
        }
        return true;
    }

    public boolean handleFactionTopMoneyCommand(Player player) {
        var topFactions = db.getTopFactionsByBalance();
        StringBuilder message = new StringBuilder("§fFaction Money Leaderboard:\n");
        
        topFactions.forEach((faction, balance) -> {
            message.append(faction)
                   .append("§f - §7$")
                   .append(balance)
                   .append("\n");
        });
        
        player.sendMessage(message.toString());
        return true;
    }

    public boolean handleFactionTopKillsCommand(Player player) {
        var topFactions = db.getTopFactionsByKills();
        StringBuilder message = new StringBuilder("§fFaction with the most kills:\n");
        
        topFactions.forEach((faction, kills) -> {
            message.append(faction)
                   .append("§f - §7")
                   .append(kills)
                   .append(" kills\n");
        });
        
        player.sendMessage(message.toString());
        return true;
    }

    public boolean handleFactionPlayersCommand(Player player, String[] args) {
        if (args.length == 1) {
            String factionName = db.getPlayerFaction(player.getUniqueId().toString());
            if (factionName != null) {
                var players = db.getFactionPlayers(factionName);
                player.sendMessage("§aPlayers in your faction " + factionName + ": " + 
                    String.join(", ", players));
            } else {
                player.sendMessage("§cYou are not in a faction.");
            }
            return true;
        } else if (args.length == 2) {
            String factionName = args[1];
            var players = db.getFactionPlayers(factionName);
            
            if (!players.isEmpty()) {
                player.sendMessage("§aPlayers in faction " + factionName + ": " + 
                    String.join(", ", players));
            } else {
                player.sendMessage("§cFaction not found. Available factions:");
                db.getAllFactionNames().forEach(faction -> 
                    player.sendMessage("§f- §7" + faction.toLowerCase()));
            }
            return true;
        }
        return false;
    }

    public boolean handleSetWarpCommand(Player player, String[] args) {
        if (!player.hasPermission("royalkingdoms.setwarp")) {
            player.sendMessage("§cYou do not have permission to set a warp.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§cUsage: /setwarp <name>");
            return true;
        }

        String warpName = args[0];
        Location playerLocation = player.getLocation();
        
        db.saveWarp(warpName, playerLocation);
        player.sendMessage("§aWarp '" + warpName + "' has been set at your current location!");
        return true;
    }

    public boolean handleWarpCommand(Player player, String[] args) {
        if (!player.hasPermission("royalkingdoms.warp")) {
            player.sendMessage("§cYou do not have permission to use /warp.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§cUsage: /warp <name>");
            return true;
        }

        String warpName = args[0];
        Location warpLocation = db.getWarp(warpName);
        
        if (warpLocation != null) {
            player.teleport(warpLocation);
            player.sendMessage("§aYou have been teleported to the warp: " + warpName);
        } else {
            player.sendMessage("§cWarp not found: " + warpName);
            player.sendMessage("§cAvailable warps: " + String.join(", ", db.getAllWarps()));
        }
        return true;
    }

    public boolean handleHomeCommand(Player player) {
        Location homeLocation = db.getHome(player.getUniqueId().toString());
        
        if (homeLocation == null) {
            player.sendMessage("§cYou need to set a home first using /sethome.");
            return true;
        }
    
        player.teleport(homeLocation);
        player.sendMessage("§aYou have been teleported to your home!");
        return true;
    }

    public boolean handleSetHomeCommand(Player player) {
        if (!"world".equals(player.getLevel().getName())) {
            player.sendMessage("§cYou can only set your home in the world 'world'.");
            return true;
        }

        if (db.getHome(player.getUniqueId().toString()) != null) {
            player.sendMessage("§cYou already have a home set.");
            return true;
        }
        
        db.saveHome(player.getUniqueId().toString(), player.getLocation());
        player.sendMessage("§aHome set successfully!");
        return true;
    }

    public boolean handleRemoveHomeCommand(Player player) {
        if (db.getHome(player.getUniqueId().toString()) == null) {
            player.sendMessage("§cYou don't have a home set to remove.");
            return true;
        }
    
        db.deleteHome(player.getUniqueId().toString());
        player.sendMessage("§aYour home has been removed.");
        return true;
    }

    public boolean handlePingCommand(Player player) {
        int ping = player.getPing();
        player.sendMessage("§aYour ping is §f" + ping + " ms");
        return true;
    }

    public boolean handleSpawnCommand(Player player) {
        player.teleport(player.getServer().getDefaultLevel().getSpawnLocation());
        player.sendMessage("§aYou have been teleported to spawn!");
        return true;
    }

    public boolean handleListWarpsCommand(Player player) {
        if (!player.hasPermission("royalkingdoms.listwarp")) {
            player.sendMessage("§cYou do not have permission to list warps.");
            return true;
        }

        var warps = db.getAllWarps();
        if (warps.isEmpty()) {
            player.sendMessage("§cNo warps have been set.");
            return true;
        }

        StringBuilder message = new StringBuilder("§aAvailable warps:\n");
        warps.forEach(warp -> message.append("§f- §7").append(warp).append("\n"));
        player.sendMessage(message.toString());
        return true;
    }

    public boolean handleDeleteWarpCommand(Player player, String[] args) {
        if (!player.hasPermission("royalkingdoms.deletewarp")) {
            player.sendMessage("§cYou do not have permission to delete warps.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§cUsage: /delwarp <name>");
            return true;
        }

        String warpName = args[0];
        if (db.deleteWarp(warpName)) {
            player.sendMessage("§aWarp '" + warpName + "' has been deleted.");
        } else {
            player.sendMessage("§cWarp '" + warpName + "' not found.");
        }
        return true;
    }

    public boolean handleUpdateHomeCommand(Player player) {
        if (db.getHome(player.getUniqueId().toString()) == null) {
            player.sendMessage("§cYou need to set a home first using /sethome.");
            return true;
        }
        
        db.saveHome(player.getUniqueId().toString(), player.getLocation());
        player.sendMessage("§aHome location updated successfully!");
        return true;
    }

    public boolean handleCreateFactionCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /f create <faction_name>");
            return true;
        }

        if (db.getPlayerFaction(player.getUniqueId().toString()) != null) {
            player.sendMessage("§cYou are already in a faction.");
            return true;
        }

        String factionName = args[1];
        if (db.factionExists(factionName)) {
            player.sendMessage("§cFaction already exists.");
            return true;
        }

        Faction newFaction = new Faction(factionName, 0.0, player.getName(), 0);
        db.createFaction(newFaction);
        db.addPlayerToFaction(player.getUniqueId().toString(), player.getName(), factionName, "Leader");
        player.sendMessage("§aFaction " + factionName + " has been created and you have joined it!");
        plugin.getScoreboardManager().updateScoreboard(player);

        return true;
    }

    public boolean handleInviteCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /f invite <player_name>");
            return true;
        }

        String playerName = args[1];
        if (playerName.equals(player.getName())) {
            player.sendMessage("§cYou cannot invite yourself.");
            return true;
        }

        Player targetPlayer = plugin.getServer().getPlayer(playerName);
        if (targetPlayer == null) {
            player.sendMessage("§cPlayer not found.");
            return true;
        }

        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        if (factionName == null) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }

        invites.put(targetPlayer.getName(), factionName);
        player.sendMessage("§aYou have invited " + playerName + " to join your faction.");
        targetPlayer.sendMessage("§aYou have been invited to join the " + factionName + " faction. Use /f join " + factionName + " to accept.");
        return true;
    }

    public boolean handleJoinFactionCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /f join <faction_name>");
            return true;
        }

        String factionName = args[1];
        if (!db.factionExists(factionName)) {
            player.sendMessage("§cFaction does not exist.");
            return true;
        }

        String invitedFaction = invites.get(player.getName());
        if (invitedFaction == null || !invitedFaction.equalsIgnoreCase(factionName)) {
            player.sendMessage("§cYou have not been invited to join this faction.");
            return true;
        }

        db.addPlayerToFaction(player.getUniqueId().toString(), player.getName(), factionName, "Member");
        db.savePlayer(player, factionName, "Member");
        invites.remove(player.getName());
        player.sendMessage("§aYou have joined the " + factionName + " faction!");
        return true;
    }

    public boolean toggleCoordinates(Player player) {
        boolean isEnabled = coordinatesEnabled.getOrDefault(player, false);

        if (isEnabled) {
            player.sendMessage("§cCoordinates display has been disabled.");
            setCoordinates(player, false);
        } else {
            player.sendMessage("§aCoordinates display has been enabled.");
            setCoordinates(player, true);
        }

        coordinatesEnabled.put(player, !isEnabled);
        return true;
    }

    public void setCoordinates(Player player, boolean enable) {
        GameRulesChangedPacket packet = new GameRulesChangedPacket();

        GameRules levelGameRules = player.getLevel().getGameRules();
        GameRules gameRules = GameRules.getDefault();
        gameRules.readNBT(levelGameRules.writeNBT());
        gameRules.setGameRule(GameRule.SHOW_COORDINATES, enable);
        packet.gameRules = gameRules;

        player.dataPacket(packet);
    }

    public void createFaction(String factionName) {
        String sql = "INSERT INTO factions (name) VALUES (?)";
        db.executeUpdate(sql, factionName);        
    }

    public void deleteFaction(String factionName) {
        dissolveAllAlliances(factionName);
        String sql = "DELETE FROM factions WHERE name = ?";
        db.executeUpdate(sql, factionName);
    }

    private void dissolveAllAlliances(String factionName) {
        List<String> allies = db.getAllies(factionName);
        for (String ally : allies) {
            db.removeAlly(factionName, ally);
            db.removeAlly(ally, factionName);
            notifyFactionMembers(factionName, "§cThe alliance with " + ally + " has been dissolved.");
            notifyFactionMembers(ally, "§cThe alliance with " + factionName + " has been dissolved.");
        }
    }

    private void showDisbandFactionConfirmation(Player player) {
        FormWindowSimple confirmGui = new FormWindowSimple("Disband Faction", "Are you sure you want to disband your faction?");
        confirmGui.addButton(new ElementButton("Yes"));
        confirmGui.addButton(new ElementButton("No"));
        player.showFormWindow(confirmGui);
    }

    public void handleDisbandFactionConfirmationResponse(Player player, FormResponseSimple response) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String buttonText = response.getClickedButton().getText();
        if (buttonText.equals("Yes")) {
            handleDisbandFactionCommand(player);
            plugin.getScoreboardManager().updateScoreboard(player);
        } else {
            handleFactionGuiCommand(player);
        }
    }

    public boolean handleDisbandFactionCommand(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        String factionName = playerData.getFaction();

        if (factionName == null) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }

        if (!playerData.isLeader()) {
            player.sendMessage("§cOnly the faction leader can disband the faction.");
            return true;
        }

        dissolveAllAlliances(factionName);
        db.deleteFaction(factionName);
        player.sendMessage("§aFaction " + factionName + " has been disbanded.");
        return true;
    }

    public boolean handlePromoteCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /f promote <player_name>");
            return true;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        String factionName = playerData.getFaction();

        if (factionName == null) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }

        if (!playerData.isLeader()) {
            player.sendMessage("§cOnly the faction leader can promote members.");
            return true;
        }

        String targetPlayerName = args[1];
        if (targetPlayerName.equals(player.getName())) {
            player.sendMessage("§cYou cannot promote yourself.");
            return true;
        }

        Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);

        if (targetPlayer == null) {
            player.sendMessage("§cPlayer not found.");
            return true;
        }

        PlayerData targetPlayerData = plugin.getPlayerDataManager().getPlayerData(targetPlayer);

        if (!factionName.equals(targetPlayerData.getFaction())) {
            player.sendMessage("§cPlayer is not in your faction.");
            return true;
        }

        targetPlayerData.setRank("Officer");
        player.sendMessage("§a" + targetPlayerName + " has been promoted to Officer.");
        return true;
    }

    public boolean handleDemoteCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /f demote <player_name>");
            return true;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        String factionName = playerData.getFaction();

        if (factionName == null) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }

        if (!playerData.isLeader()) {
            player.sendMessage("§cOnly the faction leader can demote members.");
            return true;
        }

        String targetPlayerName = args[1];
        if (targetPlayerName.equals(player.getName())) {
            player.sendMessage("§cYou cannot demote yourself.");
            return true;
        }

        Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);

        if (targetPlayer == null) {
            player.sendMessage("§cPlayer not found.");
            return true;
        }

        PlayerData targetPlayerData = plugin.getPlayerDataManager().getPlayerData(targetPlayer);

        if (!factionName.equals(targetPlayerData.getFaction())) {
            player.sendMessage("§cPlayer is not in your faction.");
            return true;
        }

        targetPlayerData.setRank("Member");
        player.sendMessage("§a" + targetPlayerName + " has been demoted to Member.");
        return true;
    }

    public boolean handleAllyCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /f ally <name> <request/accept/reject/dissolve>");
            return true;
        }

        String targetFaction = args[1];
        String action = args[2];
        String playerFaction = db.getPlayerFaction(player.getUniqueId().toString());

        if (playerFaction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }

        if (targetFaction.equalsIgnoreCase(playerFaction)) {
            player.sendMessage("§cYou cannot send an ally request to your own faction.");
            return true;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        if (!playerData.isLeader()) {
            player.sendMessage("§cOnly the faction leader can manage alliances.");
            return true;
        }

        List<String> currentAllies = db.getAllies(playerFaction);
        if (!currentAllies.isEmpty() && !action.equalsIgnoreCase("dissolve")) {
            player.sendMessage("§cYou can only have one ally at a time. Dissolve the current alliance first.");
            return true;
        }

        switch (action.toLowerCase()) {
            case "request":
                if (db.factionExists(targetFaction)) {
                    allyRequests.put(playerFaction, targetFaction);
                    player.sendMessage("§aYou have sent an alliance request to " + targetFaction + ".");
                } else {
                    player.sendMessage("§cFaction " + targetFaction + " does not exist.");
                }
                break;
            case "accept":
                if (allyRequests.containsKey(targetFaction) && allyRequests.get(targetFaction).equals(playerFaction)) {
                    db.addAlly(playerFaction, targetFaction);
                    db.addAlly(targetFaction, playerFaction);
                    allyRequests.remove(targetFaction);
                    player.sendMessage("§aYou have accepted the alliance request from " + targetFaction + ".");
                } else {
                    player.sendMessage("§cNo alliance request from " + targetFaction + ".");
                }
                break;
            case "reject":
                if (allyRequests.containsKey(targetFaction) && allyRequests.get(targetFaction).equals(playerFaction)) {
                    allyRequests.remove(targetFaction);
                    player.sendMessage("§aYou have rejected the alliance request from " + targetFaction + ".");
                } else {
                    player.sendMessage("§cNo alliance request from " + targetFaction + ".");
                }
                break;
            case "dissolve":
                if (db.isAlly(playerFaction, targetFaction)) {
                    db.removeAlly(playerFaction, targetFaction);
                    db.removeAlly(targetFaction, playerFaction);
                    notifyFactionMembers(playerFaction, "§cThe alliance with " + targetFaction + " has been dissolved.");
                    notifyFactionMembers(targetFaction, "§cThe alliance with " + playerFaction + " has been dissolved.");
                    player.sendMessage("§aYou have dissolved the alliance with " + targetFaction + ".");
                } else {
                    player.sendMessage("§cYou are not allied with " + targetFaction + ".");
                }
                break;
            default:
                player.sendMessage("§cInvalid action. Use request, accept, reject, or dissolve.");
                break;
        }
        return true;
    }

    private void notifyFactionMembers(String factionName, String message) {
        List<String> players = db.getFactionPlayers(factionName);
        for (String playerName : players) {
            Player player = plugin.getServer().getPlayer(playerName);
            if (player != null) {
                player.sendMessage(message);
            } else {
                // Save the message to be sent when the player logs in
                db.saveOfflineMessage(playerName, message);
            }
        }
    }

    public boolean handleFactionSetHomeCommand(Player player) {
        if (!"world".equals(player.getLevel().getName())) {
            player.sendMessage("§cYou can only set the faction home in the world 'world'.");
            return true;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        String factionName = playerData.getFaction();

        if (factionName == null) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }

        if (!playerData.isLeader()) {
            player.sendMessage("§cOnly the faction leader can set the faction home.");
            return true;
        }

        Location playerLocation = player.getLocation();
        db.saveWarp(factionName + "-base", playerLocation);
        player.sendMessage("§aFaction home set successfully!");
        return true;
    }

    public boolean handleFactionGuiCommand(Player player) {
        FormWindowSimple gui = new FormWindowSimple("Faction Management", "Select an action:");

        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        boolean isLeader = factionName != null && db.getFactionInfo(factionName).getFactionLeader().equals(player.getName());

        if (factionName == null) {
            gui.addButton(new ElementButton("Join Faction"));
            gui.addButton(new ElementButton("Create Faction"));
        } else {
            if (isLeader) {
                gui.addButton(new ElementButton("Leader Tools"));
            }
            gui.addButton(new ElementButton("Faction Home"));
            gui.addButton(new ElementButton("Deposit Money"));
            gui.addButton(new ElementButton("Faction Info"));
            gui.addButton(new ElementButton("Top Money"));
            gui.addButton(new ElementButton("Top Kills"));
            gui.addButton(new ElementButton("Leave Faction"));
        }
        player.showFormWindow(gui);
        return true;
    }

    public void handleGuiResponse(Player player, FormResponseSimple response) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String buttonText = response.getClickedButton().getText();

        switch (buttonText) {
            case "Join Faction":
                showFactionInvites(player);
                break;
            case "Leave Faction":
                showLeaveFactionConfirmation(player);
                break;
            case "Faction Info":
                showFactionList(player);
                break;
            case "Top Money":
                handleFactionTopMoneyCommand(player);
                break;
            case "Deposit Money":
                showDepositMoneyForm(player);
                break;
            case "Top Kills":
                handleFactionTopKillsCommand(player);
                break;
            case "Faction Players":
                handleFactionPlayersCommand(player, new String[]{"players"});
                break;
            case "Faction Home":
                handleFactionHomeCommand(player);
                break;
            case "Create Faction":
                showCreateFactionForm(player);
                break;
            case "Leader Tools":
                showLeaderTools(player);
                break;
            default:
                player.sendMessage("§cInvalid action.");
                break;
        }
    }

    private void showLeaveFactionConfirmation(Player player) {
        FormWindowSimple confirmGui = new FormWindowSimple("Leave Faction", "Are you sure you want to leave your faction?");
        confirmGui.addButton(new ElementButton("Yes"));
        confirmGui.addButton(new ElementButton("No"));
        player.showFormWindow(confirmGui);
    }

    public void handleLeaveFactionConfirmationResponse(Player player, FormResponseSimple response) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String buttonText = response.getClickedButton().getText();
        if (buttonText.equals("Yes")) {
            handleLeaveFactionCommand(player);
            plugin.getScoreboardManager().updateScoreboard(player);
        } else {
            handleFactionGuiCommand(player);
        }
    }

    private void showFactionList(Player player) {
        FormWindowSimple factionListGui = new FormWindowSimple("Faction List", "Select a faction to view info:");

        db.getAllFactionNames().forEach(factionName -> {
            factionListGui.addButton(new ElementButton(factionName));
        });

        factionListGui.addButton(new ElementButton("Back"));
        player.showFormWindow(factionListGui);
    }

    public void handleFactionListGuiResponse(Player player, FormResponseSimple response) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String buttonText = response.getClickedButton().getText();
        if (buttonText.equals("Back")) {
            handleFactionGuiCommand(player);
        } else {
            showFactionInfo(player, buttonText);
        }
    }

    private void showFactionInfo(Player player, String factionName) {
        Faction faction = db.getFactionInfo(factionName);
        
        if (faction == null) {
            player.sendMessage("§cFaction not found.");
            return;
        }
        List<String> players = db.getFactionPlayers(factionName);
        List<String> onlinePlayers = players.stream()
            .filter(name -> plugin.getServer().getPlayer(name) != null)
            .collect(Collectors.toList());
        List<String> offlinePlayers = players.stream()
            .filter(name -> plugin.getServer().getPlayer(name) == null)
            .collect(Collectors.toList());
        FormWindowSimple factionInfoGui = new FormWindowSimple("Faction Info: " + factionName, "");

        factionInfoGui.setContent(
            "Leader: " + faction.getFactionLeader() + "\n" +
            "Balance: $" + faction.getVaultBalance() + "\n" +
            "Kills: " + faction.getKills() + "\n" +
            "§7[" + onlinePlayers.size() + "/" + players.size() + "] "+ "§e" + String.join(", ", onlinePlayers) + "§7" +String.join(", ", offlinePlayers)
        );

        factionInfoGui.addButton(new ElementButton("Back"));
        player.showFormWindow(factionInfoGui);
    }

    private void showLeaderTools(Player player) {
        FormWindowSimple leaderGui = new FormWindowSimple("Leader Tools", "Select an action:");

        leaderGui.addButton(new ElementButton("Invite Player"));
        leaderGui.addButton(new ElementButton("Promote Member"));
        leaderGui.addButton(new ElementButton("Demote Member"));
        leaderGui.addButton(new ElementButton("Faction Shield"));
        leaderGui.addButton(new ElementButton("Manage Alliances"));
        leaderGui.addButton(new ElementButton("Set Faction Home"));
        leaderGui.addButton(new ElementButton("Disband Faction"));
        leaderGui.addButton(new ElementButton("Back"));

        player.showFormWindow(leaderGui);
    }

    public void handleLeaderGuiResponse(Player player, FormResponseSimple response) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String buttonText = response.getClickedButton().getText();

        switch (buttonText) {
            case "Invite Player":
                showOnlinePlayersNotInFaction(player);
                break;
            case "Promote Member":
                showFactionMembers(player, "promote");
                break;
            case "Demote Member":
                showFactionMembers(player, "demote");
                break;
            case "Faction Shield":
                plugin.getFactionShieldManager().showShieldGui(player);
                break;
            case "Manage Alliances":
                showAllianceManagement(player);
                break;
            case "Set Faction Home":
                handleFactionSetHomeCommand(player);
                break;
            case "Disband Faction":
                showDisbandFactionConfirmation(player);
                break;
            case "Back":
                handleFactionGuiCommand(player);
                break;
            default:
                player.sendMessage("§cInvalid action.");
                break;
        }
    }

    private void showOnlinePlayersNotInFaction(Player player) {
        FormWindowSimple inviteGui = new FormWindowSimple("Invite Player", "Select a player to invite:");

        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        if (factionName == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        plugin.getServer().getOnlinePlayers().values().forEach(onlinePlayer -> {
            if (!factionName.equals(db.getPlayerFaction(onlinePlayer.getUniqueId().toString()))) {
                inviteGui.addButton(new ElementButton(onlinePlayer.getName()));
            }
        });

        inviteGui.addButton(new ElementButton("Back"));
        player.showFormWindow(inviteGui);
    }

    private void showFactionMembers(Player player, String action) {
        FormWindowSimple memberGui = new FormWindowSimple(action.equals("promote") ? "Promote Member" : "Demote Member", "Select a member to " + action + ":");

        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        if (factionName == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        db.getFactionPlayers(factionName).forEach(memberName -> {
            if (!memberName.equals(player.getName())) {
                memberGui.addButton(new ElementButton(memberName));
            }
        });

        memberGui.addButton(new ElementButton("Back"));
        player.showFormWindow(memberGui);
    }

    public void handlePromoteDemoteGuiResponse(Player player, FormResponseSimple response, String action) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String buttonText = response.getClickedButton().getText();
        if (buttonText.equals("Back")) {
            showLeaderTools(player);
        } else {
            if (action.equals("promote")) {
                handlePromoteCommand(player, new String[]{"promote", buttonText});
            } else {
                handleDemoteCommand(player, new String[]{"demote", buttonText});
            }
        }
    }

    private void showFactionInvites(Player player) {
        FormWindowSimple inviteGui = new FormWindowSimple("Faction Invites", "Select a faction to join:");

        invites.forEach((playerName, factionName) -> {
            if (playerName.equals(player.getName())) {
                inviteGui.addButton(new ElementButton(factionName));
            }
        });

        inviteGui.addButton(new ElementButton("Back"));
        player.showFormWindow(inviteGui);
    }

    public void handleInvitePlayerGuiResponse(Player player, FormResponseSimple response) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String buttonText = response.getClickedButton().getText();
        if (buttonText.equals("Back")) {
            showLeaderTools(player);
        } else {
            handleInviteCommand(player, new String[]{"invite", buttonText});
        }
    }

    public void handleInviteGuiResponse(Player player, FormResponseSimple response) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String buttonText = response.getClickedButton().getText();
        if (buttonText.equals("Back")) {
            handleFactionGuiCommand(player);
        } else {
            handleJoinFactionCommand(player, new String[]{"join", buttonText});
        }
    }

    private void showAllianceManagement(Player player) {
        FormWindowSimple allianceGui = new FormWindowSimple("Manage Alliances", "Select a faction to manage alliance:");

        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        if (factionName == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        db.getAllFactionNames().forEach(otherFactionName -> {
            if (!otherFactionName.equals(factionName)) {
                allianceGui.addButton(new ElementButton(otherFactionName));
            }
        });

        allianceGui.addButton(new ElementButton("Back"));
        player.showFormWindow(allianceGui);
    }

    public void handleAllianceManagementGuiResponse(Player player, FormResponseSimple response) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String buttonText = response.getClickedButton().getText();
        if (buttonText.equals("Back")) {
            showLeaderTools(player);
        } else {
            showAllianceActions(player, buttonText);
        }
    }

    private void showAllianceActions(Player player, String targetFaction) {
        FormWindowSimple allianceActionsGui = new FormWindowSimple("Alliance Actions", "Select an action for " + targetFaction + ":");

        allianceActionsGui.addButton(new ElementButton("Request Alliance"));
        allianceActionsGui.addButton(new ElementButton("Accept Alliance"));
        allianceActionsGui.addButton(new ElementButton("Reject Alliance"));

        String playerFaction = db.getPlayerFaction(player.getUniqueId().toString());
        boolean isAllied = db.isAlly(playerFaction, targetFaction);
        if (isAllied) {
            allianceActionsGui.addButton(new ElementButton("Dissolve Alliance"));
        } else {
            allianceActionsGui.addButton(new ElementButton("§7Dissolve Alliance (Not in an alliance)"));
        }

        allianceActionsGui.addButton(new ElementButton("Back"));

        player.showFormWindow(allianceActionsGui);
    }

    public void handleAllianceActionsGuiResponse(Player player, FormResponseSimple response, String targetFaction) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String buttonText = response.getClickedButton().getText();
        if (buttonText.equals("Back")) {
            showAllianceManagement(player);
        } else {
            switch (buttonText) {
                case "Request Alliance":
                    handleAllyCommand(player, new String[]{"ally", targetFaction, "request"});
                    break;
                case "Accept Alliance":
                    handleAllyCommand(player, new String[]{"ally", targetFaction, "accept"});
                    break;
                case "Reject Alliance":
                    handleAllyCommand(player, new String[]{"ally", targetFaction, "reject"});
                    break;
                case "Dissolve Alliance":
                    handleAllyCommand(player, new String[]{"ally", targetFaction, "dissolve"});
                    break;
                default:
                    player.sendMessage("§cInvalid action.");
                    break;
            }
        }
    }

    private void showCreateFactionForm(Player player) {
        FormWindowCustom createFactionForm = new FormWindowCustom("Create Faction");
        createFactionForm.addElement(new ElementInput("Faction Name", "Enter faction name"));
        player.showFormWindow(createFactionForm);
    }

    public void handleCreateFactionFormResponse(Player player, FormResponseCustom response) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String factionName = response.getInputResponse(0);
        if (factionName == null || factionName.trim().isEmpty()) {
            player.sendMessage("§cFaction name cannot be empty.");
            return;
        }

        handleCreateFactionCommand(player, new String[]{"create", factionName});
    }

    private void showDepositMoneyForm(Player player) {
        FormWindowCustom depositMoneyForm = new FormWindowCustom("Deposit Money");
        depositMoneyForm.addElement(new ElementInput("Amount", "Enter amount to deposit"));
        player.showFormWindow(depositMoneyForm);
    }

    public void handleDepositMoneyFormResponse(Player player, FormResponseCustom response) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String amountStr = response.getInputResponse(0);
        if (amountStr == null || amountStr.trim().isEmpty()) {
            player.sendMessage("§cAmount cannot be empty.");
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            handleFactionDepositCommand(player, new String[]{"deposit", amountStr});
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount.");
        }
    }

    public boolean handleChatCommand(Player player) {
        ChatMode currentMode = chatModes.getOrDefault(player, ChatMode.ALL);
        ChatMode newMode;
        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        List<String> allies = factionName != null ? db.getAllies(factionName) : new ArrayList<>();

        switch (currentMode) {
            case ALL:
                newMode = ChatMode.FACTION;
                break;
            case FACTION:
                newMode = allies.isEmpty() ? ChatMode.ALL : ChatMode.ALLY;
                break;
            case ALLY:
            default:
                newMode = ChatMode.ALL;
                break;
        }

        chatModes.put(player, newMode);
        player.sendMessage("§aChat mode set to " + newMode.name().toLowerCase() + ".");
        return true;
    }

    public boolean handleSpyCommand(Player player) {
        if (spyMode.contains(player)) {
            spyMode.remove(player);
            player.sendMessage("§cSpy mode disabled.");
        } else {
            spyMode.add(player);
            player.sendMessage("§aSpy mode enabled.");
        }
        return true;
    }

    public void handlePlayerChat(Player player, String message) {
        ChatMode mode = chatModes.getOrDefault(player, ChatMode.ALL);
        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        List<Player> recipients = new ArrayList<>();

        switch (mode) {
            case FACTION:
                if (factionName != null) {
                    recipients = db.getFactionPlayers(factionName).stream()
                        .map(name -> plugin.getServer().getPlayer(name))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                }
                break;
            case ALLY:
                if (factionName != null) {
                    List<String> allies = db.getAllies(factionName);
                    allies.add(factionName); // Include the player's own faction
                    recipients = allies.stream()
                        .flatMap(ally -> db.getFactionPlayers(ally).stream())
                        .map(name -> plugin.getServer().getPlayer(name))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                }
                break;
            case ALL:
            default:
                recipients.addAll(plugin.getServer().getOnlinePlayers().values());
                break;
        }

        for (Player recipient : recipients) {
            recipient.sendMessage("§7[" + mode.name().toLowerCase() + "] " + player.getName() + ": " + message);
        }

        for (Player spy : spyMode) {
            spy.sendMessage("§c[Spy] " + player.getName() + ": " + message);
        }
    }

    public boolean handleAdminCommand(Player player) {
        if (!player.hasPermission("royalkingdoms.f.admin")) {
            player.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        showAdminGui(player);
        return true;
    }

    private void showAdminGui(Player player) {
        FormWindowSimple adminGui = new FormWindowSimple("Admin Faction Management", "Select a faction to manage:");

        db.getAllFactionNames().forEach(factionName -> {
            adminGui.addButton(new ElementButton(factionName));
        });

        player.showFormWindow(adminGui);
    }

    private void showFactionAdminOptions(Player player, String factionName) {
        FormWindowSimple factionAdminOptionsGui = new FormWindowSimple("Manage Faction: " + factionName, "Select an action:");

        factionAdminOptionsGui.addButton(new ElementButton("Modify Vault Balance"));
        factionAdminOptionsGui.addButton(new ElementButton("Modify Kills"));
        factionAdminOptionsGui.addButton(new ElementButton("Change Leader"));
        factionAdminOptionsGui.addButton(new ElementButton("View Shield Locations"));
        factionAdminOptionsGui.addButton(new ElementButton("Back"));

        player.showFormWindow(factionAdminOptionsGui);
    }

    public void handleAdminGuiResponse(Player player, FormResponseSimple response) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String factionName = response.getClickedButton().getText();
        plugin.getLogger().info("Admin GUI response: " + factionName);
        showFactionAdminOptions(player, factionName);
    }

    public void handleFactionAdminOptionsResponse(Player player, FormResponseSimple response, String factionName) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String buttonText = response.getClickedButton().getText();
        plugin.getLogger().info("Faction Admin Options response: " + buttonText);

        switch (buttonText) {
            case "Modify Vault Balance":
                showModifyVaultBalanceForm(player, factionName);
                break;
            case "Modify Kills":
                showModifyKillsForm(player, factionName);
                break;
            case "Change Leader":
                showChangeLeaderForm(player, factionName);
                break;
            case "View Shield Locations":
                showShieldLocations(player, factionName);
                break;
            case "Back":
                showAdminGui(player);
                break;
            default:
                player.sendMessage("§cInvalid action.");
                break;
        }
    }

    private void showModifyVaultBalanceForm(Player player, String factionName) {
        FormWindowCustom modifyVaultBalanceForm = new FormWindowCustom("Modify Vault Balance: " + factionName);
        modifyVaultBalanceForm.addElement(new ElementInput("New Vault Balance", "Enter new vault balance"));
        player.showFormWindow(modifyVaultBalanceForm);
    }

    public void handleModifyVaultBalanceFormResponse(Player player, FormResponseCustom response, String factionName) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String balanceStr = response.getInputResponse(0);
        plugin.getLogger().info("Modify Vault Balance response: " + balanceStr);

        if (balanceStr == null || balanceStr.trim().isEmpty()) {
            player.sendMessage("§cVault balance cannot be empty.");
            return;
        }

        try {
            double balance = Double.parseDouble(balanceStr);
            Faction faction = db.getFactionInfo(factionName);
            if (faction != null) {
                faction.setVaultBalance(balance);
                db.saveFaction(faction);
                player.sendMessage("§aVault balance updated successfully!");
            } else {
                player.sendMessage("§cFaction not found.");
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid vault balance.");
        }
    }

    private void showModifyKillsForm(Player player, String factionName) {
        FormWindowCustom modifyKillsForm = new FormWindowCustom("Modify Kills: " + factionName);
        modifyKillsForm.addElement(new ElementInput("New Kills", "Enter new kills number"));
        player.showFormWindow(modifyKillsForm);
    }

    public void handleModifyKillsFormResponse(Player player, FormResponseCustom response, String factionName) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String killsStr = response.getInputResponse(0);
        plugin.getLogger().info("Modify Kills response: " + killsStr);

        if (killsStr == null || killsStr.trim().isEmpty()) {
            player.sendMessage("§cKills number cannot be empty.");
            return;
        }

        try {
            int kills = Integer.parseInt(killsStr);
            Faction faction = db.getFactionInfo(factionName);
            if (faction != null) {
                faction.setKills(kills);
                db.saveFaction(faction);
                player.sendMessage("§aKills number updated successfully!");
            } else {
                player.sendMessage("§cFaction not found.");
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid kills number.");
        }
    }

    private void showChangeLeaderForm(Player player, String factionName) {
        FormWindowCustom changeLeaderForm = new FormWindowCustom("Change Leader: " + factionName);
        changeLeaderForm.addElement(new ElementInput("New Leader", "Enter new leader's name"));
        player.showFormWindow(changeLeaderForm);
    }

    public void handleChangeLeaderFormResponse(Player player, FormResponseCustom response, String factionName) {
        if (response == null || processedResponses.contains(response.hashCode())) {
            return;
        }
        processedResponses.add(response.hashCode());
        String newLeader = response.getInputResponse(0);
        plugin.getLogger().info("Change Leader response: " + newLeader);

        if (newLeader == null || newLeader.trim().isEmpty()) {
            player.sendMessage("§cLeader name cannot be empty.");
            return;
        }

        Faction faction = db.getFactionInfo(factionName);
        faction.setLeader(newLeader);
        db.saveFaction(faction);
        player.sendMessage("§aLeader updated successfully!");
    }

    private void showShieldLocations(Player player, String factionName) {
        List<ProtectedChunkData> claims = protectedChunks.get(factionName);

        if (claims == null || claims.isEmpty()) {
            player.sendMessage("§cThis faction has no claimed chunks.");
            return;
        }

        player.sendMessage("§aClaimed chunks for faction " + factionName + ":");
        for (ProtectedChunkData chunk : claims) {
            player.sendMessage("§7- Chunk at (" + chunk.getChunkX() + ", " + chunk.getChunkZ() + ")");
        }
    }

    public boolean handleBordersCommand(Player player) {
        boolean isEnabled = chunkBordersEnabled.getOrDefault(player, false);

        if (isEnabled) {
            chunkBordersEnabled.put(player, false);
            player.sendMessage("§cChunk borders disabled.");
        } else {
            chunkBordersEnabled.put(player, true);
            player.sendMessage("§aChunk borders enabled.");
            showChunkBorders(player);
        }

        return true;
    }

    private void showChunkBorders(Player player) {
        if (!chunkBordersEnabled.getOrDefault(player, false)) {
            return;
        }

        int chunkX = player.getChunkX();
        int chunkZ = player.getChunkZ();
        Level level = player.getLevel();

        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 16; z++) {
                if (x == 0 || x == 16 || z == 0 || z == 16) {
                    for (int y = 0; y < 256; y += 5) {
                        Vector3 particlePos = new Vector3(chunkX * 16 + x, y, chunkZ * 16 + z);
                        DustParticle particle = new DustParticle(particlePos, 255, 255, 255); // White particles
                        level.addParticle(particle);
                    }
                }
            }
        }

        // Schedule the next update
        new NukkitRunnable() {
            @Override
            public void run() {
                if (chunkBordersEnabled.getOrDefault(player, false)) {
                    showChunkBorders(player);
                }
            }
        }.runTaskLater(plugin, 20); // Update every second
    }
}