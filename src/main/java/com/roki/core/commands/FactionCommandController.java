package com.roki.core.commands;

import com.roki.core.Faction;
import com.roki.core.PlayerData;
import com.roki.core.RoyalKingdomsCore;
import com.roki.core.database.DataModel;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.level.Location;
import me.onebone.economyapi.EconomyAPI;

import java.util.Arrays;

public class FactionCommandController {
    private final RoyalKingdomsCore plugin;
    private final DataModel dataModel;

    public FactionCommandController(RoyalKingdomsCore plugin) {
        this.plugin = plugin;
        this.dataModel = plugin.getDataModel();

    }

    public boolean handleFactionHomeCommand(Player player, String[] args) {
        Faction faction = plugin.getPlayerFaction(player);
        
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
    
        String factionName = faction.getName().toLowerCase();
        
        // If no additional argument, use faction's default home
        if (args.length == 1) {
            Location homeLocation = plugin.getWarpLocation(factionName + "-base");
            if (homeLocation != null) {
                player.teleport(homeLocation);
                player.sendMessage("§aTeleported to " + faction.getName() + " faction base.");
                return true;
            } else {
                player.sendMessage("§cNo base location set for your faction.");
                return true;
            }
        }
        
        // If an argument is provided, validate it
        if (args.length == 2) {
            String requestedFaction = args[1].toLowerCase();
            
            // Check if the requested faction is valid
            if (Arrays.asList("red", "green", "blue").contains(requestedFaction)) {
                Location homeLocation = plugin.getWarpLocation(requestedFaction + "-base");
                if (homeLocation != null) {
                    player.teleport(homeLocation);
                    player.sendMessage("§aTeleported to " + requestedFaction + " faction base.");
                    return true;
                } else {
                    player.sendMessage("§cNo base location set for the " + requestedFaction + " faction.");
                    return true;
                }
            } else {
                player.sendMessage("§cInvalid faction. Use red, green, or blue.");
                return true;
            }
        }
        
        return false;
    }

    public boolean handleFactionInfoCommand(Player player, String[] args) {
        if (args.length == 1) {
            // Show info about the player's current faction
            Faction faction = plugin.getPlayerFaction(player);
            if (faction != null) {
                player.sendMessage("§fYour faction: " + faction.getColor() + " " + faction.getName());
                player.sendMessage("§fPlayers: §7" + String.join(", ", faction.getPlayers()));
                player.sendMessage("§fVault Balance: §7$" + faction.getVaultBalance());
            } else {
                player.sendMessage("§cYou are not in a faction.");
            }
            return true;
        } else if (args.length == 2) {
            // Show info about the specified faction
            String factionName = args[1];
            Faction faction = plugin.getFactions().stream()
                .filter(f -> f.getName().equalsIgnoreCase(factionName))
                .findFirst()
                .orElse(null);
    
            if (faction != null) {
                player.sendMessage("§fFaction: " + faction.getColor() + " " + faction.getName());
                player.sendMessage("§fPlayers: §7" + String.join(", ", faction.getPlayers()));
                player.sendMessage("§fVault Balance: §7$" + faction.getVaultBalance());
            } else {
                player.sendMessage("§cFaction not found. Available factions:");
                for (Faction f : plugin.getFactions()) {
                    player.sendMessage("§f- §7" + f.getName().toLowerCase());
                }
            }
            return true;
        }
        return false;
    }

    public boolean handleLeaveFactionCommand(Player player) {
        PlayerData playerData = new PlayerData(player);
        Faction faction = plugin.getPlayerFaction(player);
        if (faction != null) {
            faction.removePlayer(player.getName());
            player.sendMessage("§aYou have left the " + faction.getName() + " faction.");
            playerData.setFaction(null);
            playerData.savePlayerData();
            faction.saveFactionData();
            return true;
        } else {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
    }

    public boolean handleJoinFactionCommand(Player player) {
        PlayerData playerData = new PlayerData(player);
        
        if (playerData.getFaction() != null) {
            player.sendMessage("§cYou are already in the " + playerData.getFaction() + " faction!");
            return true;
        }
    
        Faction smallestFaction = plugin.findFactionWithLeastPlayers();
    
        if (smallestFaction != null) {
            smallestFaction.addPlayer(player.getName());
            playerData.setFaction(smallestFaction.getName().toLowerCase());
            smallestFaction.saveFactionData();
            playerData.savePlayerData();
            player.sendMessage("§aYou have joined the " + smallestFaction.getName() + " faction!");
            return true;
        }
    
        player.sendMessage("§cCould not join a faction at this time.");
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

            Faction faction = plugin.getPlayerFaction(player);
            if (faction == null) {
                player.sendMessage("§cYou are not in a faction.");
                return true;
            }

            double playerBalance = EconomyAPI.getInstance().myMoney(player);
            if (playerBalance < amount) {
                player.sendMessage("§cYou do not have enough money to deposit.");
                return true;
            }

            EconomyAPI.getInstance().reduceMoney(player, amount);
            faction.deposit(amount);
            player.sendMessage("§aYou have deposited " + amount + " into the " + faction.getName() + " faction's vault.");
            return true;

        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount.");
            return true;
        }
    }

    public boolean handleFactionMoneyCommand(Player player, String[] args) {
        Faction faction = plugin.getPlayerFaction(player);
        if (args.length > 1) {
            String factionName = args[1];
            faction = plugin.getFactions().stream()
                .filter(f -> f.getName().equalsIgnoreCase(factionName))
                .findFirst()
                .orElse(null);
                if (faction != null) {
                    player.sendMessage("§aFaction " + faction.getName() + "'s vault balance: §7$" + faction.getVaultBalance());
                } else {
                    player.sendMessage("§cFaction not found.");
                }
        }
        if (faction != null) {
            player.sendMessage("§aYour faction's vault balance: §7$" + faction.getVaultBalance());
        } else {
            player.sendMessage("§cYou are not in a faction.");
        }
        return true;
    }

    public boolean handleFactionTopMoneyCommand(Player player) {
        StringBuilder message = new StringBuilder("§fFaction Money Leaderboard:\n");

        plugin.getFactions().stream()
            .sorted((f1, f2) -> Double.compare(f2.getVaultBalance(), f1.getVaultBalance()))
            .forEach(faction -> {
                message.append(faction.getColor())
                       .append(" ")
                       .append(faction.getName())
                       .append("§f - §7$")
                       .append(faction.getVaultBalance())
                       .append("\n");
            });

        player.sendMessage(message.toString());
        return true;
    }

    public boolean handleFactionTopKillsCommand(Player player) {
        StringBuilder message = new StringBuilder("§fFaction with the most kills:\n");

        plugin.getFactions().forEach(faction -> {
            message.append(faction.getColor())
                   .append(" ")
                   .append(faction.getName())
                   .append("§f - §7")
                   .append(faction.getKills())
                   .append(" kills\n");
        });

        player.sendMessage(message.toString());
        return true;
    }

    public boolean handleFactionPlayersCommand(Player player, String[] args) {
        if (args.length == 1) {
            Faction faction = plugin.getPlayerFaction(player);
            if (faction != null) {
                player.sendMessage("§aPlayers in your faction " + faction.getName() + ": " + 
                    String.join(", ", faction.getPlayers()));
            } else {
                player.sendMessage("§cYou are not in a faction.");
            }
            return true;
        } else if (args.length == 2) {
            String factionName = args[1];
            Faction faction = plugin.getFactions().stream()
                .filter(f -> f.getName().equalsIgnoreCase(factionName))
                .findFirst()
                .orElse(null);
    
            if (faction != null) {
                player.sendMessage("§aPlayers in faction " + faction.getName() + ": " + 
                    String.join(", ", faction.getPlayers()));
            } else {
                player.sendMessage("§cFaction not found. Available factions:");
                plugin.getFactions().forEach(f -> 
                    player.sendMessage("§f- §7" + f.getName().toLowerCase()));
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
        
        plugin.getWarpsConfig().set(warpName + ".world", playerLocation.getLevel().getName());
        plugin.getWarpsConfig().set(warpName + ".x", playerLocation.getX());
        plugin.getWarpsConfig().set(warpName + ".y", playerLocation.getY());
        plugin.getWarpsConfig().set(warpName + ".z", playerLocation.getZ());
        plugin.getWarpsConfig().set(warpName + ".yaw", playerLocation.getYaw());
        plugin.getWarpsConfig().set(warpName + ".pitch", playerLocation.getPitch());
        plugin.getWarpsConfig().save();

        player.sendMessage("§aWarp '" + warpName + "' has been set at your current location!");
        return true;
    }

    public boolean handleWarpCommand(Player player, String warpName) {
        if (!player.hasPermission("royalkingdoms.warp")) {
            player.sendMessage("§cYou do not have permission to use /warp.");
            return true;
        }

        Location warpLocation = plugin.getWarpLocation(warpName);
        if (warpLocation != null) {
            player.teleport(warpLocation);
            player.sendMessage("§aYou have been teleported to the warp: " + warpName);
        } else {
            player.sendMessage("§cWarp not found: " + warpName);
        }
        return true;
    }

    public boolean handleHomeCommand(Player player) {
        PlayerData playerData = new PlayerData(player);
        
        if (!playerData.hasHome()) {
            player.sendMessage("§cYou need to set a home first using /sethome.");
            return true;
        }
    
        player.teleport(playerData.getHome());
        player.sendMessage("§aYou have been teleported to your home!");
        return true;
    }

    public boolean handleSetHomeCommand(Player player) {
        if (dataModel.getHome(player) != null) {
            player.sendMessage("§cYou already have a home set.");
            return true;
        }
        
        dataModel.saveHome(player, player.getLocation());
        player.sendMessage("§aHome set successfully!");
        return true;
    }

    public boolean handleRemoveHomeCommand(Player player) {
        PlayerData playerData = new PlayerData(player);
        
        if (!playerData.hasHome()) {
            player.sendMessage("§cYou don't have a home set to remove.");
            return true;
        }
    
        playerData.removeHome();
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
}