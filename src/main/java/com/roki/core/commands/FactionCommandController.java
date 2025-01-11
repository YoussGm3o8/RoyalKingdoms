package com.roki.core.commands;

import cn.nukkit.Player;
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

public class FactionCommandController {
    private final RoyalKingdomsCore plugin;
    private final DataModel dataModel;
    private final DatabaseManager db;
    private final Map<String, String> invites = new HashMap<>();

    public FactionCommandController(RoyalKingdomsCore plugin) {
        this.plugin = plugin;
        this.dataModel = plugin.getDataModel();
        this.db = dataModel.getDatabaseManager();
    }

    public boolean handleFactionHomeCommand(Player player, String[] args) {
        String factionName = db.getPlayerFaction(player.getUniqueId().toString());
        
        if (factionName == null) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
        
        // If no additional argument, use faction's default home
        if (args.length == 1) {
            Location homeLocation = db.getWarp(factionName + "-base");
            if (homeLocation != null) {
                player.teleport(homeLocation);
                player.sendMessage("§aTeleported to " + factionName + " faction base.");
            } else {
                player.sendMessage("§cNo base location set for your faction.");
            }
            return true;
        }
        
        // If an argument is provided, validate it
        if (args.length == 2) {
            String requestedFaction = args[1].toLowerCase();
            if (db.factionExists(requestedFaction)) {
                Location homeLocation = db.getWarp(requestedFaction + "-base");
                if (homeLocation != null) {
                    player.teleport(homeLocation);
                    player.sendMessage("§aTeleported to " + requestedFaction + " faction base.");
                } else {
                    player.sendMessage("§cNo base location set for the " + requestedFaction + " faction.");
                }
            } else {
                player.sendMessage("§cInvalid faction. Use red, green, or blue.");
            }
            return true;
        }
        
        return false;
    }

    public boolean handleFactionInfoCommand(Player player, String[] args) {
        if (args.length == 1) {
            // Show info about player's current faction
            String factionName = db.getPlayerFaction(player.getUniqueId().toString());
            if (factionName != null) {
                Faction info = db.getFactionInfo(factionName);
                player.sendMessage("§fYour faction: " + info.getName());
                player.sendMessage("§fPlayers: §7" + String.join(", ", db.getFactionPlayers(factionName)));
                player.sendMessage("§fVault Balance: §7$" + info.getVaultBalance());
            } else {
                player.sendMessage("§cYou are not in a faction.");
            }
            return true;
        } else if (args.length == 2) {
            String requestedFaction = args[1];
            Faction info = db.getFactionInfo(requestedFaction);
            
            if (info != null) {
                player.sendMessage("§fFaction: " + info.getName());
                player.sendMessage("§fPlayers: §7" + String.join(", ", db.getFactionPlayers(requestedFaction)));
                player.sendMessage("§fVault Balance: §7$" + info.getVaultBalance());
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

    public boolean handleJoinFactionCommand(Player player) {
        if (db.getPlayerFaction(player.getUniqueId().toString()) != null) {
            player.sendMessage("§cYou are already in a faction!");
            return true;
        }

        String smallestFaction = db.getFactionWithLeastPlayers();
        if (smallestFaction != null) {
            db.addPlayerToFaction(player.getUniqueId().toString(), player.getName(), smallestFaction);
            player.sendMessage("§aYou have joined the " + smallestFaction + " faction!");
        } else {
            player.sendMessage("§cCould not join a faction at this time.");
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

        String factionName = args[1];
        if (db.factionExists(factionName)) {
            player.sendMessage("§cFaction already exists.");
            return true;
        }

        Faction newFaction = new Faction(factionName, 0.0);
        db.saveFaction(newFaction);
        db.addPlayerToFaction(player.getUniqueId().toString(), player.getName(), factionName);
        db.savePlayer(player, factionName);
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

        db.addPlayerToFaction(player.getUniqueId().toString(), player.getName(), factionName);
        db.savePlayer(player, factionName);
        invites.remove(player.getName());
        player.sendMessage("§aYou have joined the " + factionName + " faction!");
        return true;
    }

    public void createFaction(String factionName) {
        String sql = "INSERT INTO factions (name) VALUES (?)";
        db.executeUpdate(sql, factionName);        
    }

    public void deleteFaction(String factionName) {
        String sql = "DELETE FROM factions WHERE name = ?";
        db.executeUpdate(sql, factionName);
    }
}