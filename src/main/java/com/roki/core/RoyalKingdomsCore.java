package com.roki.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.roki.core.Entities.Minotaur;
import com.roki.core.Portals.PortalEventListener;
import com.roki.core.chunkProtection.FactionShieldManager;
import com.roki.core.commands.FactionCommandController;
import com.roki.core.commands.PortalCommandController;
import com.roki.core.database.DataModel;
import com.roki.core.database.DatabaseManager;
import cn.nukkit.Player;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.custom.EntityManager;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.NukkitRunnable;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;
import me.onebone.economyapi.EconomyAPI;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.form.response.FormResponseSimple;
import cn.nukkit.form.response.FormResponseCustom;
import org.sqlite.JDBC;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.event.player.PlayerDeathEvent;

public class RoyalKingdomsCore extends PluginBase implements Listener {
    private DataModel dataModel;
    private PlayerDataManager playerDataManager;
    private FactionCommandController commandController;
    public PortalCommandController portalCommandController;
    private DatabaseManager dbManager;
    private TeleportManager teleportManager;
    private CombatManager combatManager;
    private ScoreboardManager scoreboardManager;
    private FactionShieldManager factionShieldManager;

    private Map<String, Faction> factions = new HashMap<>();
    private Config warpsConfig;
    //private Config playerDataConfig;
    private Map<Player, Boolean> scoreboardEnabled = new HashMap<>();
    
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
        // Create default config
        saveDefaultConfig();
        
        // Load SQLite JDBC driver
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            getLogger().error("Failed to load SQLite JDBC driver", e);
        }

        // Entities registration
        EntityManager.get().registerDefinition(Minotaur.DEFINITION);
    }
    
    @Override
    public void onEnable() {
        getDataFolder().mkdirs();

        // Initialize database
        dbManager = new DatabaseManager(this);
        dbManager.executeUpdate(DataModel.CREATE_FACTIONS_TABLE);
        dbManager.executeUpdate(DataModel.CREATE_PORTALS_TABLE);

        dataModel = new DataModel(this);
        factions = dataModel.loadAllFactions();
        
        // Initialize managers
        playerDataManager = new PlayerDataManager(dbManager);
        scoreboardManager = new ScoreboardManager(this);
        combatManager = new CombatManager(this);
        teleportManager = new TeleportManager(this, combatManager);
        portalCommandController = new PortalCommandController(this, dbManager);
        commandController = new FactionCommandController(this);
        factionShieldManager = new FactionShieldManager(this, dbManager);
        
        // Setup tab completion
        FactionCommand factionCommand = new FactionCommand();
        getServer().getScheduler().scheduleTask(this, new NukkitRunnable() {
            @Override
            public void run() {
                getLogger().info("Faction Tab Completer setup");
                FactionTabCompleter.setupTabCompletion(factionCommand);
            }
        });
        getServer().getCommandMap().register("f", factionCommand);

        // Register event listeners
        getServer().getPluginManager().registerEvents(new FactionEventListener(this), this);
        getServer().getPluginManager().registerEvents(new PortalEventListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);
        
        // Register commands
        registerCommands();
        
        // Start scoreboard update task
        int updateInterval = getConfig().getInt("scoreboard.update_interval", 20); // Default 1 second
        startScoreboardUpdateTask(updateInterval);
        
        getLogger().info("Royal Kingdoms Core Plugin has been enabled!");
    }
    
    /**
     * Register all plugin commands
     */
    private void registerCommands() {
        // Register /f command
        getServer().getCommandMap().register("f", new Command("f", "Factions command with various subcommands", "/f <subcommand>") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }

                Player player = (Player) sender;
                getScoreboardManager().updateScoreboard(player);

                if (args.length == 0) {
                    player.sendMessage("§7Available faction commands:");
                    player.sendMessage("§f/f create <name> §7- Create a faction");
                    player.sendMessage("§f/f join <name> §7- Join a faction after being invited");
                    player.sendMessage("§f/f invite <player> §7- Invite a player to your faction");
                    player.sendMessage("§f/f leave §7- Leave your current faction");
                    player.sendMessage("§f/f disband §7- Disband your faction (Leader only)");
                    player.sendMessage("§f/f promote <player> §7- Promote a member to officer");
                    player.sendMessage("§f/f demote <player> §7- Demote an officer to member");
                    player.sendMessage("§f/f info (<name>) §7- View faction info");
                    player.sendMessage("§f/f home §7- Go to your faction's base, if there is one...");
                    player.sendMessage("§f/f money (<name>) §7- View your/a faction's money");
                    player.sendMessage("§f/f topmoney §7- View the faction money leaderboard");
                    player.sendMessage("§f/f deposit <amount> §7- Deposit money into your faction's vault");
                    player.sendMessage("§f/f topkills §7- View the faction with the top kills");
                    player.sendMessage("§f/f players §7- View the players in your faction");
                    player.sendMessage("§f/f shield §7- Claim land for your faction");
                    player.sendMessage("§f/f admin §7- Admin faction management");
                    return true;
                }

                switch (args[0].toLowerCase()) {
                    case "create":
                        return commandController.handleCreateFactionCommand(player, args);
                    case "invite":
                        return commandController.handleInviteCommand(player, args);
                    case "join":
                        return commandController.handleJoinFactionCommand(player, args);
                    case "leave":
                        return commandController.handleLeaveFactionCommand(player);
                    case "disband":
                        return commandController.handleDisbandFactionCommand(player);
                    case "promote":
                        return commandController.handlePromoteCommand(player, args);
                    case "demote":
                        return commandController.handleDemoteCommand(player, args);
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
                        return teleportManager.teleportToFactionHome(player);
                    case "sethome":
                        return commandController.handleSetHomeCommand(player);
                    case "gui":
                        return commandController.handleFactionGuiCommand(player);
                    case "chat":
                    case "c":
                        return commandController.handleChatCommand(player);
                    case "spy":
                        return commandController.handleSpyCommand(player);
                    case "shield":
                        return factionShieldManager.handleShieldCommand(player);
                    case "admin":
                        return commandController.handleAdminCommand(player);
                    default:
                        player.sendMessage("§cInvalid subcommand. Usage: /f <gui|join|leave|info|chat|money|topmoney|deposit|topkills|players|home|sethome|demote|promote>");
                        return true;
                }
            }
        });

        // Register /wild command
        getServer().getCommandMap().register("wild", new Command("wild", "Teleport to a random location", "/wild") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }
                Player player = (Player) sender;
                return teleportManager.teleportToWild(player);
            }
        });
        
        // Register /spawn command
        getServer().getCommandMap().register("spawn", new Command("spawn", "Teleport to spawn", "/spawn") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }
                Player player = (Player) sender;
                return teleportManager.teleportToSpawn(player);
            }
        });
        
        // Register /home command
        getServer().getCommandMap().register("home", new Command("home", "Teleport to your home", "/home") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }
                Player player = (Player) sender;
                return teleportManager.teleportToHome(player);
            }
        });
        
        // Register /sethome command
        getServer().getCommandMap().register("sethome", new Command("sethome", "Set your home location", "/sethome") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }
                Player player = (Player) sender;
                PlayerData playerData = getPlayerDataManager().getPlayerData(player);
                
                // Save player's current location as home
                Location home = player.getLocation();
                playerData.setHome(home);
                player.sendMessage(TextFormat.GREEN + "Home location set!");
                
                return true;
            }
        });
        
        // Register /removehome command
        getServer().getCommandMap().register("removehome", new Command("removehome", "Remove your home", "/removehome") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }
                Player player = (Player) sender;
                return commandController.handleRemoveHomeCommand(player);
            }
        });
        
        // Register /warp command
        getServer().getCommandMap().register("warp", new Command("warp", "Teleport to a warp location", "/warp <name>") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }
                
                Player player = (Player) sender;
                
                if (args.length == 0) {
                    player.sendMessage(TextFormat.RED + "Usage: /warp <warp_name>");
                    return true;
                }
                
                return teleportManager.teleportToWarp(player, args[0]);
            }
        });
        
        // Register /setwarp command
        getServer().getCommandMap().register("setwarp", new Command("setwarp", "Create a warp at your location", "/setwarp <name>") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }
                
                return commandController.handleSetWarpCommand((Player) sender, args);
            }
        });
        
        // Register /scoreboard command
        getServer().getCommandMap().register("scoreboard", new Command("scoreboard", "Toggle the scoreboard display", "/scoreboard") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }
                
                return handleScoreboardCommand((Player) sender);
            }
        });
        
        // Register /pvpscoreboard command
        getServer().getCommandMap().register("pvpscoreboard", new Command("pvpscoreboard", "Toggle the PVP scoreboard", "/pvpscoreboard") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }
                
                Player player = (Player) sender;
                scoreboardManager.togglePvpScoreboard(player);
                return true;
            }
        });
        
        // Register /ping command
        getServer().getCommandMap().register("ping", new Command("ping", "Show your ping to the server", "/ping") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }
                
                return commandController.handlePingCommand((Player) sender);
            }
        });
        
        // Register /borders command
        getServer().getCommandMap().register("borders", new Command("borders", "Toggle chunk borders", "/borders") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }

                Player player = (Player) sender;
                return commandController.handleBordersCommand(player);
            }
        });
        
        // Register /xyz command
        getServer().getCommandMap().register("xyz", new Command("xyz", "Toggle coordinates display", "/xyz") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }

                Player player = (Player) sender;
                return commandController.toggleCoordinates(player);
            }
        });
        
        // Portal commands
        getServer().getCommandMap().register("setportal", new Command("setportal", "Create a portal", "/setportal <name> <destination>") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }

                return portalCommandController.handleSetPortalCommand((Player)sender, args);
            }
        });

        getServer().getCommandMap().register("portalstick", new Command("portalstick", "Get portal creation stick", "/portalstick") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }

                return portalCommandController.handlePortalStickCommand((Player)sender);
            }
        });
        
        // Minotaur commands
        getServer().getCommandMap().register("summonminotaur", new Command("summonminotaur", "Summons a minotaur", "/summonminotaur") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }

                Player player = (Player) sender;
                Entity entity = Entity.createEntity(Minotaur.IDENTIFIER, player);
                if (entity != null) {
                    entity.spawnToAll();
                    player.sendMessage("Minotaur summoned!");
                    return true;
                }
                
                return false;
            }
        });
    }
    
    /**
     * Start the scoreboard update task
     * 
     * @param updateInterval Ticks between updates
     */
    private void startScoreboardUpdateTask(int updateInterval) {
        getServer().getScheduler().scheduleRepeatingTask(this, new NukkitRunnable() {
            @Override
            public void run() {
                for (Player player : getServer().getOnlinePlayers().values()) {
                    PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId().toString());
                    if (playerData != null && playerData.isPvpScoreboardEnabled()) {
                        scoreboardManager.updatePvpScoreboard(player);
                    } else {
                        scoreboardManager.updateScoreboard(player);
                    }
                }
            }
        }, updateInterval);
    }
    
    @Override
    public void onDisable() {
        // Save faction data to database
        for (Faction faction : factions.values()) {
            dataModel.saveFaction(faction);
            dbManager.deleteFactionIfEmpty(faction.getName());
        }
        
        // Clean up resources
        playerDataManager.saveAll();
        playerDataManager.clearCache();
        
        getLogger().info("Royal Kingdoms Core Plugin has been disabled!");
    }
    
    /**
     * Toggle the scoreboard for a player
     * 
     * @param player The player
     * @return true if command was successful
     */
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
        String factionName = dbManager.getPlayerFaction(player.getUniqueId().toString());
        if (factionName == null) {
            return null;
        }
        return factions.get(factionName.toLowerCase());
    }
    
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
    
    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
    
    public FactionCommandController getCommandController() {
        return commandController;
    }

    public FactionShieldManager getFactionShieldManager() {
        return factionShieldManager;
    }
    
    public TeleportManager getTeleportManager() {
        return teleportManager;
    }
    
    public CombatManager getCombatManager() {
        return combatManager;
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
    
    /**
     * Handle form close event for simple form
     * 
     * @param player The player
     * @param window The form window
     */
    public void handleFormClose(Player player, FormWindowSimple window) {
        // Handle form close logic here, if needed
        getScoreboardManager().updateScoreboard(player);
        if (!window.getTitle().equals("Faction Management")) {
            commandController.handleFactionGuiCommand(player);
        }
    }

    /**
     * Handle form close event for custom form
     * 
     * @param player The player
     * @param window The form window
     */
    public void handleFormClose(Player player, FormWindowCustom window) {
        // Handle form close logic here, if needed
        getScoreboardManager().updateScoreboard(player);
        commandController.handleFactionGuiCommand(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerFormResponded(PlayerFormRespondedEvent event) {
        if (event.getWindow() instanceof FormWindowSimple) {
            FormWindowSimple window = (FormWindowSimple) event.getWindow();
            FormResponseSimple response = (FormResponseSimple) event.getResponse();
            if (response == null) {
                // Handle case where the player closed the form without making a selection
                handleFormClose(event.getPlayer(), window);
                return;
            }

            // Add debugging info
            getLogger().info("FormWindowSimple Title: " + window.getTitle());
            getLogger().info("FormWindowSimple Response: " + response.getClickedButton().getText());

            if (window.getTitle().equals("Faction Management")) {
                commandController.handleGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Faction Invites")) {
                commandController.handleInviteGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Leader Tools")) {
                commandController.handleLeaderGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Faction List")) {
                commandController.handleFactionListGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Invite Player")) {
                commandController.handleInvitePlayerGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Promote Member")) {
                commandController.handlePromoteDemoteGuiResponse(event.getPlayer(), response, "promote");
            } else if (window.getTitle().equals("Demote Member")) {
                commandController.handlePromoteDemoteGuiResponse(event.getPlayer(), response, "demote");
            } else if (window.getTitle().equals("Manage Alliances")) {
                commandController.handleAllianceManagementGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().startsWith("Alliance Actions: ")) {
                String targetFaction = window.getTitle().substring("Alliance Actions: ".length());
                commandController.handleAllianceActionsGuiResponse(event.getPlayer(), response, targetFaction);
            } else if (window.getTitle().equals("Leave Faction")) {
                commandController.handleLeaveFactionConfirmationResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Disband Faction")) {
                commandController.handleDisbandFactionConfirmationResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Claim Land")) {
                factionShieldManager.handleShieldGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Manage Permissions")) {
                factionShieldManager.handlePermissionGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Your Claims")) {
                factionShieldManager.handleClaimsGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Unclaim Chunk")) {
                factionShieldManager.handleUnclaimConfirmationResponse(event.getPlayer(), response);
            }
        } else if (event.getWindow() instanceof FormWindowCustom) {
            FormWindowCustom window = (FormWindowCustom) event.getWindow();
            FormResponseCustom response = (FormResponseCustom) event.getResponse();
            
            // Add debugging info
            getLogger().info("FormWindowCustom Title: " + window.getTitle());
            getLogger().info("FormWindowCustom Response: " + (response != null ? response.toString() : "null"));

            if (response == null) {
                // Handle case where the player closed the form without making a selection
                handleFormClose(event.getPlayer(), window);
                return;
            }

            if (window.getTitle().equals("Create Faction")) {
                commandController.handleCreateFactionFormResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Deposit Money")) {
                commandController.handleDepositMoneyFormResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Member Permissions")) {
                factionShieldManager.handleMemberPermissionGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Ally Permissions")) {
                factionShieldManager.handleAllyPermissionGuiResponse(event.getPlayer(), response);
            } else if (window.getTitle().equals("Shield Reactor")) {
                factionShieldManager.handleShieldReactorGuiResponse(event.getPlayer(), response);
            }
        }
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        commandController.handlePlayerChat(player, message);
        event.setCancelled(true);
    }
}