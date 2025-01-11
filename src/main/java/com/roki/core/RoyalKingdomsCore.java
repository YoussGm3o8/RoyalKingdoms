package com.roki.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import com.roki.core.Entities.PhoenixEntity;
import com.roki.core.Portals.PortalEventListener;
import com.roki.core.commands.FactionCommandController;
import com.roki.core.commands.PortalCommandController;
import com.roki.core.database.DataModel;
import com.roki.core.database.DatabaseManager;
import com.roki.core.PlayerData;

import cn.nukkit.Player;
import cn.nukkit.level.Location;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.custom.EntityManager;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.NukkitRunnable;
import cn.nukkit.utils.Config;

public class RoyalKingdomsCore extends PluginBase {
    private DataModel dataModel;
    private PlayerDataManager playerDataManager;
    private FactionCommandController commandController;
    public PortalCommandController portalCommandController;
    private DatabaseManager dbManager;

    private Map<String, Faction> factions = new HashMap<>();
    private Config warpsConfig;
    //private Config playerDataConfig;
    private Map<Player, Boolean> scoreboardEnabled = new HashMap<>();
    private ScoreboardManager scoreboardManager;


    // Season day counter and leader votes
    private boolean seasonActive; // Whether a season is currently active


    // private SRegionProtectorMain regionProtector;
    private static RoyalKingdomsCore instance;
    public RoyalKingdomsCore() {

        instance = this;

    }



    public static RoyalKingdomsCore getInstance() {

        return instance;

    }

    public Collection<Faction> getFactions() {
        return factions.values();
    }

    @Override
    public void onLoad() {
        // Entities registration
        EntityManager.get().registerDefinition(PhoenixEntity.DEFINITION);
    }

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();

        dbManager = new DatabaseManager(this);
        dbManager.executeUpdate(DataModel.CREATE_FACTIONS_TABLE);
        dbManager.executeUpdate(DataModel.CREATE_PORTALS_TABLE);

        dataModel = new DataModel(this);
        factions = dataModel.loadAllFactions();

        playerDataManager = new PlayerDataManager(dbManager);
        portalCommandController = new PortalCommandController(this, dbManager);
        // initializePremadeFactions();
        warpsConfig = new Config(new File(getDataFolder(), "warps.yml"), Config.YAML);
        portalCommandController.portalConfig = new Config(new File(getDataFolder(), "portals.yml"), Config.YAML);

        scoreboardManager = new ScoreboardManager(this);
        
        // Setup tab completion
        commandController = new FactionCommandController(this);
        
        FactionCommand factionCommand = new FactionCommand();
        getServer().getScheduler().scheduleTask(this, new NukkitRunnable() {
            @Override
            public void run() {
                getLogger().info("Faction Tab Completer setup");
                FactionTabCompleter.setupTabCompletion(factionCommand);
            }
     
        });
        getServer().getCommandMap().register("f", factionCommand);

        getServer().getPluginManager().registerEvents(new FactionEventListener(this), this);
        getServer().getPluginManager().registerEvents(new PortalEventListener(this), this);

        getLogger().info("Royal Kingdoms Core Plugin has been enabled!");
    }



    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public static FactionCommand getFactionCommand() {
        return new FactionCommand();
    }


    @Override
    public void onDisable() {

        for (Faction faction : factions.values()) {
            dbManager.deleteFactionIfEmpty(faction.getName());
        }
        
        // playerDataManager.saveAll();
        // playerDataManager.clearCache();
        getLogger().info("Royal Kingdoms Core Plugin has been disabled!");
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        getScoreboardManager().updateScoreboard(player);

        if (command.getName().equalsIgnoreCase("f")) {
            if (args.length > 0) {
                switch (args[0].toLowerCase()) {
                    case "create":
                        return commandController.handleCreateFactionCommand((Player) sender, args);
                    case "invite":
                        return commandController.handleInviteCommand((Player) sender, args);
                    case "join":
                        return commandController.handleJoinFactionCommand((Player) sender, args);
                }
            }
        }
        if (command.getName().equalsIgnoreCase("setportal")) {
            return portalCommandController.handleSetPortalCommand((Player)sender, args);
        }

        if (command.getName().equalsIgnoreCase("portalstick")) {
            return portalCommandController.handlePortalStickCommand((Player)sender);
        }


        if (command.getName().equals("summondragon")) {
            Entity entity = Entity.createEntity(PhoenixEntity.IDENTIFIER, (Player) player);
            if (entity != null) {
                entity.spawnToAll();
                player.sendMessage("Dragon summoned!");
                return true;
            }
        }


        if (command.getName().equalsIgnoreCase("setwarp")) {
            return commandController.handleSetWarpCommand(player, args);
        }

        if (command.getName().equalsIgnoreCase("scoreboard")) {
            return handleScoreboardCommand(player);
        }

        if (command.getName().equalsIgnoreCase("f")) {
            boolean result = handleFactionCommand(player, args);

            return result;
        }

        switch (command.getName().toLowerCase()) {
            case "ping":
                return commandController.handlePingCommand(player);
            case "spawn":
                return commandController.handleSpawnCommand(player);
            case "sethome":
                return commandController.handleSetHomeCommand(player);
            case "home":
                return commandController.handleHomeCommand(player);
            case "removehome":
                return commandController.handleRemoveHomeCommand(player);
            case "warp":
                if (args.length == 0) {
                    player.sendMessage("§cUsage: /warp <warp_name>");
                    return true;
                }
                return commandController.handleWarpCommand(player, args);
            default:
            return super.onCommand(sender, command, label, args);
            }
            
    }

    public Config getWarpsConfig() {
        return warpsConfig;
    }

    // Retrieve a warp location by name
    public Location getWarpLocation(String name) {
        return dataModel.getWarp(name);
    }

    public DataModel getDataModel() {
        return dataModel;
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
            player.sendMessage("§f/f create <name> §7- Create a faction");
            player.sendMessage("§f/f join <name> §7- Join a faction after being invited");
            player.sendMessage("§f/f invite <player> §7- Invite a player to your faction");
            player.sendMessage("§f/f leave §7- Leave your current faction");
            player.sendMessage("§f/f info (<name>) §7- View faction info");
            player.sendMessage("§f/f home §7- Go to your faction's base, if there is one...");
            player.sendMessage("§f/f money (<name>) §7- View your/a faction's money");
            player.sendMessage("§f/f topmoney §7- View the faction money leaderboard");
            player.sendMessage("§f/f deposit <amount> §7- Deposit money into your faction's vault");
            player.sendMessage("§f/f topkills §7- View the faction with the top kills");
            player.sendMessage("§f/f players §7- View the players in your faction");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "join":
                return commandController.handleJoinFactionCommand(player);
            case "leave":
                return commandController.handleLeaveFactionCommand(player);
            case "info":
                return commandController.handleFactionInfoCommand(player, args);
            case "money":
                return commandController.handleFactionMoneyCommand(player, args);
            case "topmoney":
                return commandController.handleFactionTopMoneyCommand(player);
            case "deposit":
                return commandController.handleFactionDepositCommand(player, args);
            case "topkills":
                return commandController.handleFactionTopKillsCommand(player);
            case "players":
                return commandController.handleFactionPlayersCommand(player, args);
            case "home":
                return commandController.handleFactionHomeCommand(player, args);
            default:
                player.sendMessage("§cInvalid subcommand. Usage: /f <join|leave|info|money|topmoney|deposit|topkills|players>");
                return true;
        }
    }
    
    public Faction findFactionWithLeastPlayers() {
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
        PlayerData playerData = new PlayerData(player, dbManager);
        String factionName = playerData.getFaction();
        if (factionName == null) {
            return null;
        }
        return factions.get(factionName.toLowerCase());
    }
    
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
}