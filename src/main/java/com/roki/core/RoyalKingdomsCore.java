package com.roki.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.roki.core.Entities.PhoenixEntity;
import com.roki.core.Entities.DragonEntity;
import com.roki.core.Entities.GriffinEntity;

import cn.nukkit.Player;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandExecutor;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.custom.EntityManager;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;

import me.onebone.economyapi.EconomyAPI;
// import sergeydertan.sregionprotector.SRegionProtectorMain;
// import sergeydertan.sregionprotector.region.Region;
// import sergeydertan.sregionprotector.region.RegionManager;

public class RoyalKingdomsCore extends PluginBase {
    private Map<String, Faction> factions = new HashMap<>();
    private Config warpsConfig;
    private Config playerDataConfig;
    private Map<Player, Boolean> scoreboardEnabled = new HashMap<>();
    private ScoreboardManager scoreboardManager;
    private FactionCommand factionCommand;
    // private SRegionProtectorMain regionProtector;

    public Collection<Faction> getFactions() {
        return factions.values();
    }

    public Config getPlayerDataConfig() {
        return playerDataConfig;
    }

    public void refreshFactionData() {
        for (Faction faction : factions.values()) {
            faction.loadFactionData(); // Reload data for each faction
        }
    
        // Reload player-to-faction mapping from playerDataConfig
        playerDataConfig.reload();
        for (String key : playerDataConfig.getKeys(false)) {
            String factionName = playerDataConfig.getString(key);
            if (factions.containsKey(factionName)) {
                Faction faction = factions.get(factionName.toLowerCase());
                if (!faction.getPlayers().contains(key)) {
                    faction.addPlayer(key); // Ensure mapping is consistent
                }
            }
        }
        getLogger().info("Faction data refreshed successfully.");
    }

    @Override
    public void onLoad() {
        // Entities registration
        EntityManager.get().registerDefinition(PhoenixEntity.DEFINITION);
        EntityManager.get().registerDefinition(GriffinEntity.DEFINITION);
        EntityManager.get().registerDefinition(DragonEntity.DEFINITION);
    }

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        initializePremadeFactions();
        warpsConfig = new Config(new File(getDataFolder(), "warps.yml"), Config.YAML);

        scoreboardManager = new ScoreboardManager(this);
        
        // Setup tab completion
        FactionCommand factionCommand = new FactionCommand();
        FactionTabCompleter.setupTabCompletion(factionCommand, this);
        getServer().getCommandMap().register("f", factionCommand);

        getServer().getPluginManager().registerEvents(new FactionEventListener(this), this);
        getLogger().info("Royal Kingdoms Core Plugin has been enabled!");
    }


    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    @Override
    public void onDisable() {
        getLogger().info("Royal Kingdoms Core Plugin has been disabled!");
    }

    private void initializePremadeFactions() {
        factions.put("phoenix", new Faction("Phoenix", "§cRed"));
        factions.put("dragon", new Faction("Dragon", "§9Blue"));
        factions.put("griffin", new Faction("Griffin", "§aGreen"));
    
        // Load data for each faction after initialization
        for (Faction faction : factions.values()) {
            faction.loadFactionData(); // Make sure the faction data is loaded
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equals("summonphoenix")) {
            Entity entity = Entity.createEntity(PhoenixEntity.IDENTIFIER, (Player) player);
            if (entity != null) {
                entity.spawnToAll();
                player.sendMessage("Phoenix summoned!");
                return true;
            }
        }
        
        if (command.getName().equals("summondragon")) {
            Entity entity = Entity.createEntity(DragonEntity.IDENTIFIER, (Player) player);
            if (entity != null) {
                entity.spawnToAll();
                player.sendMessage("Dragon summoned!");
                return true;
            }
        }


        if (command.getName().equalsIgnoreCase("setwarp")) {
            return handleSetWarpCommand(player, args);
        }

        if (command.getName().equalsIgnoreCase("scoreboard")) {
            return handleScoreboardCommand(player);
        }

        if (command.getName().equalsIgnoreCase("f")) {
            boolean result = handleFactionCommand(player, args);

            // Update the player's scoreboard after the command
            getScoreboardManager().updateScoreboard(player);

            return result;
        }

        switch (command.getName().toLowerCase()) {
            case "ping":
                return handlePingCommand(player);
            case "spawn":
                return handleSpawnCommand(player);
            case "sethome":
                return handleSetHomeCommand(player);
            case "home":
                return handleHomeCommand(player);
            case "removehome":
                return handleRemoveHomeCommand(player);
            case "warp":
                if (args.length == 0) {
                    player.sendMessage("§cUsage: /warp <warp_name>");
                    return true;
                }
                return handleWarpCommand(player, args[0]);
            default:
                return false;
            }
            
    }

    // Handle the /setwarp <name> command
    private boolean handleSetWarpCommand(Player player, String[] args) {
        if (!player.hasPermission("royalkingdoms.setwarp")) {
            player.sendMessage("§cYou do not have permission to set a warp.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§cUsage: /setwarp <name>");
            return true;
        }

        String warpName = args[0];

        // Get the current location of the player
        Location playerLocation = player.getLocation();

        // Save the warp location in the config
        warpsConfig.set(warpName + ".world", playerLocation.getLevel().getName());
        warpsConfig.set(warpName + ".x", playerLocation.getX());
        warpsConfig.set(warpName + ".y", playerLocation.getY());
        warpsConfig.set(warpName + ".z", playerLocation.getZ());
        warpsConfig.set(warpName + ".yaw", playerLocation.getYaw());
        warpsConfig.set(warpName + ".pitch", playerLocation.getPitch());
        warpsConfig.save();

        player.sendMessage("§aWarp '" + warpName + "' has been set at your current location!");

        return true;
    }

    // /ping command
    private boolean handlePingCommand(Player player) {
        int ping = player.getPing();
        player.sendMessage("§aYour ping is §f" + ping + " ms");
        return true;
    }

    // /spawn command (teleport to spawn)
    private boolean handleSpawnCommand(Player player) {
        Position spawnLocation = player.getServer().getDefaultLevel().getSpawnLocation(); // Default spawn location
        player.teleport(spawnLocation);
        player.sendMessage("§aYou have been teleported to spawn!");
        return true;
    }
    
    private boolean handleSetHomeCommand(Player player) {
        PlayerData playerData = new PlayerData(player);
        
        if (playerData.hasHome()) {
            player.sendMessage("§cYou already have a home set.");
            return true;
        }
    
        playerData.setHome(player.getLocation());
        player.sendMessage("§aHome set successfully!");
        return true;
    }

    private boolean handleHomeCommand(Player player) {
        PlayerData playerData = new PlayerData(player);
        
        if (!playerData.hasHome()) {
            player.sendMessage("§cYou need to set a home first using /sethome.");
            return true;
        }
    
        player.teleport(playerData.getHome());
        player.sendMessage("§aYou have been teleported to your home!");
        return true;
    }

    // Handle the /removehome command (delete player's home)
    private boolean handleRemoveHomeCommand(Player player) {
        PlayerData playerData = new PlayerData(player);
        
        if (!playerData.hasHome()) {
            player.sendMessage("§cYou don't have a home set to remove.");
            return true;
        }
    
        playerData.removeHome(); 
        player.sendMessage("§aYour home has been removed.");
        return true;
    }

    // Handle the /warp command to teleport the player to a warp location
    private boolean handleWarpCommand(Player player, String warpName) {
        if (!player.hasPermission("royalkingdoms.warp")) {
            player.sendMessage("§cYou do not have permission to use /warp.");
            return true;
        }

        Location warpLocation = getWarpLocation(warpName);
        if (warpLocation != null) {
            player.teleport(warpLocation);
            player.sendMessage("§aYou have been teleported to the warp: " + warpName);
        } else {
            player.sendMessage("§cWarp not found: " + warpName);
        }
        return true;
    }


    // Retrieve a warp location by name
    public Location getWarpLocation(String warpName) {
        if (warpsConfig.exists(warpName)) {
            String worldName = warpsConfig.getString(warpName + ".world");
            double x = warpsConfig.getDouble(warpName + ".x");
            double y = warpsConfig.getDouble(warpName + ".y");
            double z = warpsConfig.getDouble(warpName + ".z");
            float yaw = (float) warpsConfig.getDouble(warpName + ".yaw");
            float pitch = (float) warpsConfig.getDouble(warpName + ".pitch");

            Level level = getServer().getLevelByName(worldName);
            return new Location(x, y, z, pitch, yaw, level);
        }
        return null;
    }
    
    private boolean handleScoreboardCommand(Player player) {
        boolean isEnabled = scoreboardEnabled.getOrDefault(player, true); // Default to true (enabled)

        if (isEnabled) {
            // Disable the scoreboard
            scoreboardManager.removeScoreboard(player);
            player.sendMessage("§cScoreboard has been disabled.");
        } else {
            // Enable the scoreboard
            scoreboardManager.createScoreboard(player);
            player.sendMessage("§aScoreboard has been enabled.");
        }

        // Toggle the scoreboard state
        scoreboardEnabled.put(player, !isEnabled);
        return true;
    }

    public List<String> getFactionNames() {
        return new ArrayList<>(factions.keySet());
    }

    private boolean handleFactionCommand(Player player, String[] args) {
        if (args.length == 0) {
            // Show list of available subcommands to the player in chat
            player.sendMessage("§7Available faction commands:");
            player.sendMessage("§f/f join §7- Join a faction");
            player.sendMessage("§f/f leave §7- Leave your current faction");
            player.sendMessage("§f/f info §7- View faction info");
            player.sendMessage("§f/f money §7- View your faction's money");
            player.sendMessage("§f/f topmoney §7- View the faction money leaderboard");
            player.sendMessage("§f/f deposit <amount> §7- Deposit money into your faction's vault");
            player.sendMessage("§f/f topkills §7- View the faction with the top kills");
            player.sendMessage("§f/f players §7- View the players in your faction");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "join":
                return handleJoinFactionCommand(player);
            case "leave":
                return handleLeaveFactionCommand(player);
                case "info":
                return handleFactionInfoCommand(player, args);
            case "money":
                return handleFactionMoneyCommand(player);
            case "topmoney":
                return handleFactionTopMoneyCommand(player);
            case "deposit":
                return handleFactionDepositCommand(player, args);
            case "topkills":
                return handleFactionTopKillsCommand(player);
                case "players":
                return handleFactionPlayersCommand(player, args);
            default:
                player.sendMessage("§cInvalid subcommand. Usage: /f <join|leave|info|money|topmoney|deposit|topkills|players>");
                return true;
        }
    }

    private boolean handleFactionInfoCommand(Player player, String[] args) {
        if (args.length == 1) {
            // Show info about the player's current faction
            Faction faction = getPlayerFaction(player);
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
            Faction faction = factions.get(factionName.toLowerCase());
    
            if (faction != null) {
                player.sendMessage("§fFaction: " + faction.getColor() + " " + faction.getName());
                player.sendMessage("§fPlayers: §7" + String.join(", ", faction.getPlayers()));
                player.sendMessage("§fVault Balance: §7$" + faction.getVaultBalance());
            } else {
                // Faction not found, show list of available factions
                player.sendMessage("§cFaction not found. Available factions:");
                for (String availableFaction : factions.keySet()) {
                    player.sendMessage("§f- §7" + availableFaction);
                }
            }
            return true;
        }
        return false;
    }

    private boolean handleLeaveFactionCommand(Player player) {
        PlayerData playerData = new PlayerData(player);
        Faction faction = getPlayerFaction(player);
        if (faction != null) {
            faction.removePlayer(player.getName());
            player.sendMessage("§aYou have left the " + faction.getName() + " faction.");
            playerData.setFaction(null); // Clear faction in player data
            return true;
        } else {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
    }

    private boolean handleJoinFactionCommand(Player player) {
        PlayerData playerData = new PlayerData(player);
        
        if (getPlayerFaction(player) != null) {
            player.sendMessage("§cYou are already in a faction!");
            return true;
        }

        Faction smallestFaction = findFactionWithLeastPlayers();

        if (smallestFaction != null) {
            smallestFaction.addPlayer(player.getName());
            player.sendMessage("§aYou have joined the " + smallestFaction.getName() + " faction!");
            playerData.setFaction(smallestFaction.getName()); // Save faction to player data
            return true;
        }

        player.sendMessage("§cCould not join a faction at this time.");
        return true;
    }

    private boolean handleFactionDepositCommand(Player player, String[] args) {
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

            // Get the player's faction
            Faction faction = getPlayerFaction(player);
            if (faction == null) {
                player.sendMessage("§cYou are not in a faction.");
                return true;
            }

            // Check if the player has enough balance and deposit it to the faction vault
            double playerBalance = EconomyAPI.getInstance().myMoney(player);
            if (playerBalance < amount) {
                player.sendMessage("§cYou do not have enough money to deposit.");
                return true;
            }

            // Reduce the player's balance
            EconomyAPI.getInstance().reduceMoney(player, amount);

            // Deposit money into the faction vault (EconomyAPI)
            faction.deposit(amount);

            player.sendMessage("§aYou have deposited " + amount + " into the " + faction.getName() + " faction's vault.");
            return true;

        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount.");
            return true;
        }
    }

    private boolean handleFactionMoneyCommand(Player player) {
        Faction faction = getPlayerFaction(player);
        if (faction != null) {
            player.sendMessage("§aYour faction's vault balance: §7$" + faction.getVaultBalance());
        } else {
            player.sendMessage("§cYou are not in a faction.");
        }
        return true;
    }

    private boolean handleFactionTopMoneyCommand(Player player) {
        StringBuilder message = new StringBuilder("§fFaction Money Leaderboard:\n");

        // Sort factions by vault balance (from EconomyAPI)
        factions.values().stream()
            .sorted((f1, f2) -> Double.compare(f2.getVaultBalance(), f1.getVaultBalance()))
            .forEach(faction -> {
                message.append(faction.getColor() + " " + faction.getName())
                       .append("§f - §7$")
                       .append(faction.getVaultBalance()) // Vault balance managed by EconomyAPI
                       .append(" \n");
            });

        player.sendMessage(message.toString());
        return true;
    }

    private boolean handleFactionTopKillsCommand(Player player) {
        StringBuilder message = new StringBuilder("§fFaction with the most kills:\n");

        factions.forEach((name, faction) -> {
            message.append(faction.getColor() + " " + faction.getName())
                   .append("§f - §7")
                   .append(faction.getKills())
                   .append(" kills\n");
        });

        player.sendMessage(message.toString());
        return true;
    }

    private boolean handleFactionPlayersCommand(Player player, String[] args) {
        if (args.length == 1) {
            // Show players in the player's current faction
            Faction faction = getPlayerFaction(player);
            if (faction != null) {
                player.sendMessage("§aPlayers in your faction " + faction.getName() + ": " + String.join(", ", faction.getPlayers()));
            } else {
                player.sendMessage("§cYou are not in a faction.");
            }
            return true;
        } else if (args.length == 2) {
            // Show players in the specified faction
            String factionName = args[1];
            Faction faction = factions.get(factionName.toLowerCase());
    
            if (faction != null) {
                player.sendMessage("§aPlayers in faction " + faction.getName() + ": " + String.join(", ", faction.getPlayers()));
            } else {
                // Faction not found, show list of available factions
                player.sendMessage("§cFaction not found. Available factions:");
                for (String availableFaction : factions.keySet()) {
                    player.sendMessage("§f- §7" + availableFaction);
                }
            }
            return true;
        }
        return false;
    }

    private Faction findFactionWithLeastPlayers() {
        if (factions.isEmpty()) return null;

        Faction smallestFaction = null;
        int smallestSize = Integer.MAX_VALUE;

        for (Faction faction : factions.values()) {
            if (faction.getPlayerCount() < smallestSize) {
                smallestFaction = faction;
                smallestSize = faction.getPlayerCount();
            }
        }

        return smallestFaction;
    }

    public Faction getPlayerFaction(Player player) {
        PlayerData playerData = new PlayerData(player);
        String factionName = playerData.getFaction();
        return factionName != null ? factions.get(factionName.toLowerCase()) : null;
    }
}