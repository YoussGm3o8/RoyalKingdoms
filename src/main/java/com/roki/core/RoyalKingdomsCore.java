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
import com.roki.core.Entities.DragonEntity;
import com.roki.core.Entities.GriffinEntity;

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
    private PlayerDataManager playerDataManager;

    private Map<String, Faction> factions = new HashMap<>();
    private Config warpsConfig;
    //private Config playerDataConfig;
    private Map<Player, Boolean> scoreboardEnabled = new HashMap<>();
    private ScoreboardManager scoreboardManager;

    // Portal PLugin
    private Config portalConfig;
    private Map<String, Position> pos1 = new HashMap<>();
    private Map<String, Position> pos2 = new HashMap<>();


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
        playerDataManager = new PlayerDataManager();
        getServer().getScheduler().scheduleRepeatingTask(this, this::checkSeasonMilestones, 20 * 60);
        
        initializePremadeFactions();
        warpsConfig = new Config(new File(getDataFolder(), "warps.yml"), Config.YAML);
        portalConfig = new Config(new File(getDataFolder(), "portals.yml"), Config.YAML);

        scoreboardManager = new ScoreboardManager(this);
        
        // Setup tab completion
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
            return handleSetPortalCommand((Player)sender, args);
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("portalstick")) {
            return handlePortalStickCommand((Player)sender);
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

    private boolean handleSetPortalCommand(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage("§cUsage: /setportal <name>");
            return true;
        }

        String portalName = args[0].toLowerCase();

        // Check if both positions are set
        if (!pos1.containsKey(player.getName()) || !pos2.containsKey(player.getName())) {
            player.sendMessage("§cYou must set both positions first using the portal stick!");
            return true;
        }

        // Get positions
        Position p1 = pos1.get(player.getName());
        Position p2 = pos2.get(player.getName());

        // Save portal data
        Map<String, Object> portalData = new HashMap<>();
        portalData.put("world", p1.getLevel().getName());
        portalData.put("x1", p1.getX());
        portalData.put("y1", p1.getY());
        portalData.put("z1", p1.getZ());
        portalData.put("x2", p2.getX());
        portalData.put("y2", p2.getY());
        portalData.put("z2", p2.getZ());
        portalData.put("command", ""); // Default empty command

        portalConfig.set(portalName, portalData);
        portalConfig.save();

        // Clear positions
        pos1.remove(player.getName());
        pos2.remove(player.getName());

        player.sendMessage("§aPortal '" + portalName + "' created!");
        return true;
    }

    public void checkPortalEntry(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Position playerPos = player.getLocation();
    
        for (String portalName : portalConfig.getKeys(false)) {
            Map<String, Object> portalData = (Map<String, Object>) portalConfig.get(portalName);
            
            // Verify portal is in the same world
            if (!playerPos.getLevel().getName().equals(portalData.get("world"))) continue;
    
            // Check portal bounds
            double x1 = ((Number) portalData.get("x1")).doubleValue();
            double y1 = ((Number) portalData.get("y1")).doubleValue();
            double z1 = ((Number) portalData.get("z1")).doubleValue();
            double x2 = ((Number) portalData.get("x2")).doubleValue();
            double y2 = ((Number) portalData.get("y2")).doubleValue();
            double z2 = ((Number) portalData.get("z2")).doubleValue();
    
            if (isWithinBounds(playerPos, x1, y1, z1, x2, y2, z2)) {
                String command = (String) portalData.getOrDefault("command", "");
                
                // Add debug logging
                getLogger().info("Portal detected: " + portalName);
                getLogger().info("Command to execute: " + command);
                
                if (!command.isEmpty()) {
                    try {
                        // Execute the command as the player
                        getServer().dispatchCommand(player, command.replace("%player%", player.getName()));
                        getLogger().info("Command executed successfully for player: " + player.getName());
                    } catch (Exception e) {
                        getLogger().warning("Failed to execute command: " + e.getMessage());
                    }
                }
            }
        }
    }

    // Utility method to check if a position is within portal bounds
    private boolean isWithinBounds(Position pos, double x1, double y1, double z1, 
                                    double x2, double y2, double z2) {
        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);
        double minZ = Math.min(z1, z2);
        double maxZ = Math.max(z1, z2);

        return pos.getX() >= minX && pos.getX() <= maxX &&
               pos.getY() >= minY && pos.getY() <= maxY &&
               pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public void handlePortalStickInteraction(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Item item = event.getItem();

        if (item != null && item.getId() == Item.STICK && 
            item.getCustomName().equals("§aPortal Creation Stick")) {
            
            if (event.getAction() == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
                // Set first position
                pos1.put(player.getName(), event.getBlock().getLocation());
                player.sendMessage("§aPosition 1 set!");
                event.setCancelled(true);
            } else if (event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
                // Set second position
                pos2.put(player.getName(), event.getBlock().getLocation());
                player.sendMessage("§aPosition 2 set!");
                event.setCancelled(true);
            }
        }
    }

    private boolean handlePortalStickCommand(Player player) {
        Item portalStick = Item.get(Item.STICK, 0, 1);
        portalStick.setCustomName("§aPortal Creation Stick");
        player.getInventory().addItem(portalStick);
        player.sendMessage("§aReceived Portal Creation Stick");
        return true;
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
            case "home":
                return handleFactionHomeCommand(player, args);
            default:
                player.sendMessage("§cInvalid subcommand. Usage: /f <join|leave|info|money|topmoney|deposit|topkills|players>");
                return true;
        }
    }

    private boolean handleFactionHomeCommand(Player player, String[] args) {
        Faction faction = getPlayerFaction(player);
        
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
    
        String factionName = faction.getName().toLowerCase();
        
        // If no additional argument, use faction's default home
        if (args.length == 1) {
            Location homeLocation = getWarpLocation(factionName + "-base");
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
                Location homeLocation = getWarpLocation(requestedFaction + "-base");
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
            playerData.savePlayerData(); // Save player data to file
            faction.saveFactionData(); // Save faction data to file
            return true;
        } else {
            player.sendMessage("§cYou are not in a faction.");
            return true;
        }
    }

    private boolean handleJoinFactionCommand(Player player) {
        PlayerData playerData = new PlayerData(player);
        
        if (playerData.getFaction() != null) {
            player.sendMessage("§cYou are already in the " + playerData.getFaction()  + " faction! " );
            return true;
        }
    
        Faction smallestFaction = findFactionWithLeastPlayers();
    
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
        return factions.get(factionName);
    }
    
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
}