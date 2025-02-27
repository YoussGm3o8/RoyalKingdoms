package com.roki.core;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.math.Vector3;
import cn.nukkit.scheduler.Task;
import cn.nukkit.utils.TextFormat;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Manages all teleportation functionality including teleportation delays,
 * spawn teleportation, and random (wild) teleport.
 */
public class TeleportManager {
    private final RoyalKingdomsCore plugin;
    private final CombatManager combatManager;
    private final Random random = new Random();
    private final int teleportDelay;
    private final String[] delayedCommands;
    private final Location spawnLocation;
    private final int spawnRadius;
    private final Map<String, Integer> pendingTeleports = new HashMap<>();

    public TeleportManager(RoyalKingdomsCore plugin, CombatManager combatManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        
        // Load teleport delay from config
        this.teleportDelay = plugin.getConfig().getInt("teleport.delay", 5);
        this.delayedCommands = plugin.getConfig().getStringList("teleport.commands").toArray(new String[0]);
        
        // Load spawn location and radius
        String worldName = plugin.getConfig().getString("spawn.world", Server.getInstance().getDefaultLevel().getName());
        double x = plugin.getConfig().getDouble("spawn.x", 0);
        double y = plugin.getConfig().getDouble("spawn.y", 64);
        double z = plugin.getConfig().getDouble("spawn.z", 0);
        float yaw = (float) plugin.getConfig().getDouble("spawn.yaw", 0);
        float pitch = (float) plugin.getConfig().getDouble("spawn.pitch", 0);
        Level world = Server.getInstance().getLevelByName(worldName);
        
        if (world == null) {
            world = Server.getInstance().getDefaultLevel();
            plugin.getLogger().warning("Spawn world '" + worldName + "' not found. Using default level.");
        }
        
        this.spawnLocation = new Location(x, y, z, yaw, pitch, world);
        this.spawnRadius = plugin.getConfig().getInt("spawn.radius", 50);
    }
    
    /**
     * Teleport a player to spawn
     * 
     * @param player The player to teleport
     * @return true if teleport was successful or initiated, false if not allowed
     */
    public boolean teleportToSpawn(Player player) {
        if (!combatManager.canTeleport(player)) {
            return false;
        }
        
        // Check if player is already in the spawn area (for instant teleport)
        if (isInSpawnArea(player.getLocation())) {
            player.teleport(spawnLocation);
            player.sendMessage(TextFormat.GREEN + "Teleported to spawn!");
            return true;
        }
        
        // Delayed teleport
        return teleportWithDelay(player, spawnLocation, "spawn");
    }
    
    /**
     * Check if a location is within the spawn area
     * 
     * @param location The location to check
     * @return true if within spawn area, false otherwise
     */
    public boolean isInSpawnArea(Location location) {
        if (!location.getLevel().getName().equals(spawnLocation.getLevel().getName())) {
            return false;
        }
        
        double distance = location.distance(new Vector3(spawnLocation.x, spawnLocation.y, spawnLocation.z));
        return distance <= spawnRadius;
    }
    
    /**
     * Teleport a player to a random wild location
     * 
     * @param player The player to teleport
     * @return true if teleport was successful or initiated, false if not allowed
     */
    public boolean teleportToWild(Player player) {
        // Check cooldown
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
        if (playerData != null && !playerData.canUseWildCommand()) {
            int remaining = playerData.getRemainingWildCooldown();
            player.sendMessage(TextFormat.RED + "You must wait " + remaining + " seconds before using /wild again!");
            return false;
        }
        
        if (!combatManager.canTeleport(player)) {
            return false;
        }
        
        // Get wild config
        String worldName = plugin.getConfig().getString("wild.world", Server.getInstance().getDefaultLevel().getName());
        Level world = Server.getInstance().getLevelByName(worldName);
        if (world == null) {
            world = Server.getInstance().getDefaultLevel();
            plugin.getLogger().warning("Wild world '" + worldName + "' not found. Using default level.");
        }
        
        // Get bounds
        int minX = plugin.getConfig().getInt("wild.bounds.pos1.x", -1000);
        int minZ = plugin.getConfig().getInt("wild.bounds.pos1.z", -1000);
        int maxX = plugin.getConfig().getInt("wild.bounds.pos2.x", 1000);
        int maxZ = plugin.getConfig().getInt("wild.bounds.pos2.z", 1000);
        int attempts = plugin.getConfig().getInt("wild.retry_attempts", 10);
        
        // Find a safe location
        Location teleportLocation = findSafeLocation(world, minX, minZ, maxX, maxZ, attempts);
        
        if (teleportLocation == null) {
            player.sendMessage(TextFormat.RED + "Failed to find a safe location. Please try again later.");
            return false;
        }
        
        // Set cooldown
        if (playerData != null) {
            int cooldown = plugin.getConfig().getInt("wild.cooldown", 300);
            playerData.setWildCooldown(cooldown);
        }
        
        player.sendMessage(TextFormat.GREEN + "Finding a random location in the wilderness...");
        return teleportWithDelay(player, teleportLocation, "wild");
    }
    
    /**
     * Find a safe location for teleportation
     * 
     * @param world The world to search in
     * @param minX Minimum X coordinate
     * @param minZ Minimum Z coordinate
     * @param maxX Maximum X coordinate
     * @param maxZ Maximum Z coordinate
     * @param attempts Number of attempts to find a safe location
     * @return A safe location or null if none found
     */
    private Location findSafeLocation(Level world, int minX, int minZ, int maxX, int maxZ, int attempts) {
        for (int i = 0; i < attempts; i++) {
            // Generate random coordinates
            int x = minX + random.nextInt(maxX - minX + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);
            
            // Find highest block at this position
            int y = world.getHighestBlockAt(x, z) + 1;
            
            // Check if location is safe (not in liquid, not in a block, has ground)
            if (y > 0 && y < 255) {
                int blockId = world.getBlockIdAt(x, y - 1, z);
                int blockAboveId = world.getBlockIdAt(x, y, z);
                int blockTwoAboveId = world.getBlockIdAt(x, y + 1, z);
                
                // Make sure the ground is solid, and there's two air blocks above
                if (blockId != 0 && blockId != 8 && blockId != 9 && blockId != 10 && blockId != 11 &&
                    blockAboveId == 0 && blockTwoAboveId == 0) {
                    return new Location(x + 0.5, y, z + 0.5, 0, 0, world);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Teleport a player to a warp location
     * 
     * @param player The player to teleport
     * @param warpName The name of the warp
     * @return true if teleport was successful or initiated, false if not allowed
     */
    public boolean teleportToWarp(Player player, String warpName) {
        if (!combatManager.canTeleport(player)) {
            return false;
        }
        
        Location warpLocation = plugin.getWarpLocation(warpName);
        if (warpLocation == null) {
            player.sendMessage(TextFormat.RED + "Warp '" + warpName + "' doesn't exist!");
            return false;
        }
        
        // Check if player is in spawn area (for instant teleport)
        if (isInSpawnArea(player.getLocation())) {
            player.teleport(warpLocation);
            player.sendMessage(TextFormat.GREEN + "Teleported to " + warpName + "!");
            return true;
        }
        
        // Delayed teleport
        return teleportWithDelay(player, warpLocation, "warp:" + warpName);
    }
    
    /**
     * Teleport a player to their home
     * 
     * @param player The player to teleport
     * @return true if teleport was successful or initiated, false if not allowed
     */
    public boolean teleportToHome(Player player) {
        if (!combatManager.canTeleport(player)) {
            return false;
        }
        
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
        if (playerData == null || playerData.getHome() == null) {
            player.sendMessage(TextFormat.RED + "You don't have a home set!");
            return false;
        }
        
        Location homeLocation = playerData.getHome();
        
        // Check if player is in spawn area (for instant teleport)
        if (isInSpawnArea(player.getLocation())) {
            player.teleport(homeLocation);
            player.sendMessage(TextFormat.GREEN + "Teleported to your home!");
            return true;
        }
        
        // Delayed teleport
        return teleportWithDelay(player, homeLocation, "home");
    }
    
    /**
     * Teleport a player to their faction home
     * 
     * @param player The player to teleport
     * @return true if teleport was successful or initiated, false if not allowed
     */
    public boolean teleportToFactionHome(Player player) {
        if (!combatManager.canTeleport(player)) {
            return false;
        }
        
        Faction faction = plugin.getPlayerFaction(player);
        if (faction == null) {
            player.sendMessage(TextFormat.RED + "You are not in a faction!");
            return false;
        }
        
        Location factionHome = faction.getHome();
        if (factionHome == null) {
            player.sendMessage(TextFormat.RED + "Your faction doesn't have a home set!");
            return false;
        }
        
        // Check if player is in spawn area (for instant teleport)
        if (isInSpawnArea(player.getLocation())) {
            player.teleport(factionHome);
            player.sendMessage(TextFormat.GREEN + "Teleported to your faction home!");
            return true;
        }
        
        // Delayed teleport
        return teleportWithDelay(player, factionHome, "faction_home");
    }
    
    /**
     * Handle teleport with delay if required
     * 
     * @param player The player to teleport
     * @param destination The destination to teleport to
     * @param teleportType The type of teleport for messaging purposes
     * @return true if teleport was initiated, false otherwise
     */
    private boolean teleportWithDelay(Player player, Location destination, String teleportType) {
        // Cancel any existing teleport
        cancelTeleport(player);
        
        // Check if command should have a delay
        boolean shouldDelay = needsDelay(teleportType);
        
        // If no delay is needed, teleport instantly
        if (!shouldDelay || teleportDelay <= 0) {
            player.teleport(destination);
            player.sendMessage(TextFormat.GREEN + "Teleported!");
            return true;
        }
        
        // Set up delayed teleport
        player.sendMessage(TextFormat.YELLOW + "Teleporting in " + teleportDelay + " seconds. Don't move!");
        
        // Get player data
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
        
        // Create teleport task
        class TeleportTask extends Task {
            private int seconds = teleportDelay;
            
            @Override
            public void onRun(int currentTick) {
                if (seconds <= 0) {
                    // Teleport the player
                    player.teleport(destination);
                    player.sendMessage(TextFormat.GREEN + "Teleported!");
                    
                    // Clean up
                    if (playerData != null) {
                        playerData.setTeleporting(false, null, -1, null);
                    }
                    pendingTeleports.remove(player.getName());
                    this.cancel();
                } else if (seconds <= 3) {
                    // Countdown for last few seconds
                    player.sendMessage(TextFormat.YELLOW + "Teleporting in " + seconds + "...");
                }
                
                seconds--;
            }
        }
        
        TeleportTask task = new TeleportTask();
        plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, task, 20);
        
        // Store teleport task ID
        int taskId = task.getTaskId();
        pendingTeleports.put(player.getName(), taskId);
        
        // Update player data
        if (playerData != null) {
            playerData.setTeleporting(true, destination, taskId, teleportType);
        }
        
        return true;
    }
    
    /**
     * Cancel a pending teleport for a player
     * 
     * @param player The player
     */
    public void cancelTeleport(Player player) {
        Integer taskId = pendingTeleports.remove(player.getName());
        if (taskId != null) {
            // Cancel the task
            Server.getInstance().getScheduler().cancelTask(taskId);
            
            // Update player data
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
            if (playerData != null) {
                playerData.cancelTeleport();
            }
            
            player.sendMessage(TextFormat.RED + "Teleport cancelled!");
        }
    }
    
    /**
     * Handle player movement during teleport
     * 
     * @param player The player who moved
     */
    public void handlePlayerMove(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId().toString());
        if (playerData != null && playerData.isTeleporting()) {
            // Cancel the teleport
            cancelTeleport(player);
        }
    }
    
    /**
     * Check if the teleportation needs a delay
     * 
     * @param teleportType The type of teleport
     * @return true if delay is needed, false otherwise
     */
    private boolean needsDelay(String teleportType) {
        String command = teleportType.split(":")[0].toLowerCase();
        for (String delayedCommand : delayedCommands) {
            if (delayedCommand.equalsIgnoreCase(command)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get the spawn location
     * 
     * @return The spawn location
     */
    public Location getSpawnLocation() {
        return spawnLocation;
    }
}