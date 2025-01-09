package com.roki.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.roki.core.Entities.PhoenixEntity;
import com.roki.core.Portals.PortalEventListener;
import com.roki.core.commands.FactionCommandController;
import com.roki.core.commands.PortalCommandController;
import com.roki.core.database.DataModel;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandExecutor;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.custom.EntityManager;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.item.Item;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;
import me.onebone.economyapi.EconomyAPI;
// import sergeydertan.sregionprotector.SRegionProtectorMain;
// import sergeydertan.sregionprotector.region.Region;
// import sergeydertan.sregionprotector.region.RegionManager;

public class RoyalKingdomsCore extends PluginBase {
    private DataModel dataModel;
    private PlayerDataManager playerDataManager;
    private FactionCommandController commandController;
    public PortalCommandController portalCommandController;

    private Map<String, Faction> factions = new HashMap<>();
    private Config warpsConfig;
    //private Config playerDataConfig;
    private Map<Player, Boolean> scoreboardEnabled = new HashMap<>();
    private ScoreboardManager scoreboardManager;


    // Season day counter and leader votes
    private int globalDayCounter = 0;
    private Map<String, Map<String, Integer>> factionVotes = new HashMap<>();
    private Map<String, Set<String>> playersVoted = new HashMap<>();
    private boolean seasonActive; // Whether a season is currently active
    private long seasonStartTime; // Timestamp for when the season started
    private boolean leadersElected;

    // private SRegionProtectorMain regionProtector;

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

        dataModel = new DataModel(this);
        factions = dataModel.loadAllFactions();

        playerDataManager = new PlayerDataManager();
        getServer().getScheduler().scheduleRepeatingTask(this, this::checkSeasonMilestones, 20 * 60);
        portalCommandController = new PortalCommandController(this);
        initializePremadeFactions();
        warpsConfig = new Config(new File(getDataFolder(), "warps.yml"), Config.YAML);
        portalCommandController.portalConfig = new Config(new File(getDataFolder(), "portals.yml"), Config.YAML);

        scoreboardManager = new ScoreboardManager(this);
        
        // Setup tab completion
        commandController = new FactionCommandController(this);
        
        FactionCommand factionCommand = new FactionCommand();
        FactionTabCompleter.setupTabCompletion(factionCommand, this);
        getServer().getCommandMap().register("f", factionCommand);

        getServer().getPluginManager().registerEvents(new FactionEventListener(this), this);
        getServer().getPluginManager().registerEvents(new PortalEventListener(this), this);

        getLogger().info("Royal Kingdoms Core Plugin has been enabled!");
    }



    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    private void checkSeasonMilestones() {
        if (!seasonActive) return;

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - seasonStartTime;

        // Check for leader election (30 days)
        if (elapsedTime >= TimeUnit.DAYS.toMillis(30) && !leadersElected) {
            electFactionLeaders();
            leadersElected = true; // Flag to ensure this runs only once
        }

        // Check for season end (120 days)
        if (elapsedTime >= TimeUnit.DAYS.toMillis(120)) {
            endSeason();
        }
    }

    private void loadSeasonData() {
        Config seasonConfig = new Config(new File(getDataFolder(), "season.yml"), Config.YAML);
        seasonActive = seasonConfig.getBoolean("seasonActive", false);
        seasonStartTime = seasonConfig.getLong("seasonStartTime", 0);
        leadersElected = seasonConfig.getBoolean("leadersElected", false);
    }

    private void saveSeasonData() {
        Config seasonConfig = new Config(new File(getDataFolder(), "season.yml"), Config.YAML);
        seasonConfig.set("seasonActive", seasonActive);
        seasonConfig.set("seasonStartTime", seasonStartTime);
        seasonConfig.set("leadersElected", leadersElected);
        seasonConfig.save();
    }

    @Override
    public void onDisable() {
        playerDataManager.saveAll();
        playerDataManager.clearCache();
        getLogger().info("Royal Kingdoms Core Plugin has been disabled!");
        saveSeasonData();
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

        if (command.getName().equalsIgnoreCase("startseason")) {
            return handleStartSeasonCommand(sender);
        }

        if (command.getName().equalsIgnoreCase("f")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("voteleader")) {
                return handleVoteLeaderCommand(sender, args);
            }
        }
        if (command.getName().equalsIgnoreCase("setportal")) {
            return portalCommandController.handleSetPortalCommand((Player)sender, args);
        }

        Player player = (Player) sender;
        getScoreboardManager().updateScoreboard(player);

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
                return commandController.handleWarpCommand(player, args[0]);
            default:
            return super.onCommand(sender, command, label, args);
            }
            
    }

    private boolean handleStartSeasonCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (seasonActive) {
            sender.sendMessage("§cA season is already active.");
            return true;
        }

        seasonActive = true;
        seasonStartTime = System.currentTimeMillis();
        sender.sendMessage("§aSeason has started!");

        return true;
    }

    public Config getWarpsConfig() {
        return warpsConfig;
    }

    private boolean handleVoteLeaderCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        Faction faction = getPlayerFaction(player);

        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /f voteleader <player_name>");
            return true;
        }

        String candidateName = args[1];
        if (!faction.getPlayers().contains(candidateName)) {
            player.sendMessage("§cPlayer is not in your faction.");
            return true;
        }

        String factionName = faction.getName().toLowerCase();
        factionVotes.putIfAbsent(factionName, new HashMap<>());
        playersVoted.putIfAbsent(factionName, new HashSet<>()); // Ensure a set exists for this faction

        Map<String, Integer> votes = factionVotes.get(factionName);
        Set<String> votedPlayers = playersVoted.get(factionName); // Get the set of players who have voted

        if (votedPlayers.contains(player.getName())) {
            player.sendMessage("§cYou have already voted for a leader.");
            return true;
        }

        int currentVotes = votes.getOrDefault(candidateName, 0);

        votes.put(candidateName, currentVotes + 1);
        player.sendMessage("§aYour vote has been cast for " + candidateName + " as faction leader.");
        return true;
    }

    private void electFactionLeaders() {
        for (String factionName : factions.keySet()) {
            Faction faction = factions.get(factionName);
            Map<String, Integer> votes = factionVotes.getOrDefault(factionName, new HashMap<>());
            Set<String> votedPlayers = playersVoted.getOrDefault(factionName, new HashSet<>());
    
            // Create a map of candidate votes, excluding those who haven't voted
            Map<String, Integer> validVotes = new HashMap<>();
            for (Map.Entry<String, Integer> entry : votes.entrySet()) {
                String candidate = entry.getKey();
                if (votedPlayers.contains(candidate)) { // Only count votes from players who have voted
                    validVotes.put(candidate, validVotes.getOrDefault(candidate, 0) + entry.getValue());
                }
            }
    
            // Elect leader based on the most votes
            String leader = validVotes.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    
            if (leader != null) {
                faction.setLeader(leader);
                getLogger().info("Leader elected for faction " + factionName + ": " + leader);
            } else {
                getLogger().info("No leader elected for faction " + factionName);
            }
        }
    
        factionVotes.clear();
        playersVoted.clear();  // Clear the players who voted map as well
        getServer().broadcastMessage("§aFaction leaders have been elected!");
    }
    private void endSeason() {
        seasonActive = false;
        seasonStartTime = 0;

        for (Faction faction : factions.values()) {
            faction.resetSeasonData(); // Implement this method in your Faction class
        }

        getServer().broadcastMessage("§cThe season has ended!");
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
            player.sendMessage("§f/f join §7- Join a faction");
            player.sendMessage("§f/f leave §7- Leave your current faction");
            player.sendMessage("§f/f info §7- View faction info");
            player.sendMessage("§f/f home §7- Go to your faction's base, if there is one...");
            player.sendMessage("§f/f money §7- View your faction's money");
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
        PlayerData playerData = new PlayerData(player);
        String factionName = playerData.getFaction();
        return factions.get(factionName);
    }
    
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
}