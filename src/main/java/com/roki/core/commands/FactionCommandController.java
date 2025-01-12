package com.roki.core.commands;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.level.Location;

import com.roki.core.Faction;
import com.roki.core.FactionTabCompleter;
import com.roki.core.PlayerData;
import com.roki.core.PlayerDataManager;
import com.roki.core.RoyalKingdomsCore;
import com.roki.core.database.DatabaseManager;
import com.roki.core.database.DataModel;
import me.onebone.economyapi.EconomyAPI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.element.ElementInput;
import cn.nukkit.form.response.FormResponseSimple;
import cn.nukkit.form.response.FormResponseCustom;

public class FactionCommandController {
    private final RoyalKingdomsCore plugin;
    private final DataModel dataModel;
    private final DatabaseManager db;
    private final Map<String, String> invites = new HashMap<>();
    private final Map<String, String> allyRequests = new HashMap<>();
    private Map<Player, Boolean> coordinatesEnabled = new HashMap<>();

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
                player.sendMessage(color +"Leader: §7$" + info.getFactionLeader());
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
        db.saveFaction(newFaction);
        db.addPlayerToFaction(player.getUniqueId().toString(), player.getName(), factionName, "Leader");
        db.savePlayer(player, factionName, "Leader");
        player.sendMessage("§aFaction " + factionName + " has been created and you have joined it!");


        PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
        PlayerData playerData = playerDataManager.getPlayerData(player);
        System.out.println(playerData.getFaction());


        
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
        } else {
            player.sendMessage("§aCoordinates display has been enabled.");
        }

        coordinatesEnabled.put(player, !isEnabled);
        return true;
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

    public boolean handleFactionSetHomeCommand(Player player, String[] args) {
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
            gui.addButton(new ElementButton("§7Join Faction (Already in a faction)"));
            gui.addButton(new ElementButton("Leave Faction"));
            gui.addButton(new ElementButton("Faction Info"));
            gui.addButton(new ElementButton("Faction Money"));
            gui.addButton(new ElementButton("Top Money"));
            gui.addButton(new ElementButton("Deposit Money"));
            gui.addButton(new ElementButton("Top Kills"));
            gui.addButton(new ElementButton("Faction Players"));
            if (isLeader) {
                gui.addButton(new ElementButton("Leader Tools"));
            } else {
                gui.addButton(new ElementButton("§7Leader Tools (Not a leader)"));
            }
        }

        player.showFormWindow(gui);
        return true;
    }

    public void handleGuiResponse(Player player, FormResponseSimple response) {
        String buttonText = response.getClickedButton().getText();

        switch (buttonText) {
            case "Join Faction":
                showFactionInvites(player);
                break;
            case "Leave Faction":
                handleLeaveFactionCommand(player);
                break;
            case "Faction Info":
                showFactionList(player);
                break;
            case "Faction Money":
                handleFactionMoneyCommand(player, new String[]{"money"});
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

    private void showFactionList(Player player) {
        FormWindowSimple factionListGui = new FormWindowSimple("Faction List", "Select a faction to view info:");

        db.getAllFactionNames().forEach(factionName -> {
            factionListGui.addButton(new ElementButton(factionName));
        });

        factionListGui.addButton(new ElementButton("Back"));
        player.showFormWindow(factionListGui);
    }

    public void handleFactionListGuiResponse(Player player, FormResponseSimple response) {
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
        leaderGui.addButton(new ElementButton("Manage Alliances"));
        leaderGui.addButton(new ElementButton("Set Faction Home"));
        leaderGui.addButton(new ElementButton("Disband Faction"));
        leaderGui.addButton(new ElementButton("Back"));

        player.showFormWindow(leaderGui);
    }

    public void handleLeaderGuiResponse(Player player, FormResponseSimple response) {
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
            case "Manage Alliances":
                showAllianceManagement(player);
                break;
            case "Set Faction Home":
                handleFactionSetHomeCommand(player, new String[]{"sethome"});
                break;
            case "Disband Faction":
                handleDisbandFactionCommand(player);
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
        String buttonText = response.getClickedButton().getText();
        if (buttonText.equals("Back")) {
            showLeaderTools(player);
        } else {
            handleInviteCommand(player, new String[]{"invite", buttonText});
        }
    }

    public void handleInviteGuiResponse(Player player, FormResponseSimple response) {
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
        allianceActionsGui.addButton(new ElementButton("Dissolve Alliance"));
        allianceActionsGui.addButton(new ElementButton("Back"));

        player.showFormWindow(allianceActionsGui);
    }

    public void handleAllianceActionsGuiResponse(Player player, FormResponseSimple response, String targetFaction) {
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
}