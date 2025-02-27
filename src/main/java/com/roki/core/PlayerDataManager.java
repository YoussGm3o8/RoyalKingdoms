package com.roki.core;

import com.roki.core.database.DatabaseManager;
import com.roki.core.database.PlayerDataModel;
import cn.nukkit.Player;

import java.util.HashMap;
import java.util.Map;

public class PlayerDataManager {
    private final DatabaseManager db;
    private final Map<String, PlayerData> playerDataCache = new HashMap<>();

    public PlayerDataManager(DatabaseManager db) {
        this.db = db;
    }

    /**
     * Get player data for a player
     * 
     * @param player the player
     * @return the player's data
     */
    public PlayerData getPlayerData(Player player) {
        return getPlayerData(player.getUniqueId().toString());
    }
    
    /**
     * Get player data for a UUID
     * 
     * @param uuid the player's UUID as string
     * @return the player's data
     */
    public PlayerData getPlayerData(String uuid) {
        // Check cache first
        if (playerDataCache.containsKey(uuid)) {
            return playerDataCache.get(uuid);
        }
        
        // Load from database
        PlayerDataModel dataModel = db.loadPlayerData(uuid);
        if (dataModel == null) {
            // Player not found in database
            return null;
        }
        
        PlayerData playerData = new PlayerData(
            dataModel.getUuid(),
            dataModel.getName(),
            dataModel.getFaction(),
            dataModel.getRank(),
            dataModel.getHome(),
            dataModel.getLastLogin(),
            dataModel.getOnlineTime()
        );
        
        // Cache the data
        playerDataCache.put(uuid, playerData);
        
        return playerData;
    }

    /**
     * Save player data to database
     * 
     * @param playerData the player data to save
     */
    public void savePlayerData(PlayerData playerData) {
        db.savePlayerData(
            playerData.getUuid(),
            playerData.getName(),
            playerData.getFaction(),
            playerData.getRank(),
            playerData.getHome(),
            playerData.getLastLogin(),
            playerData.getOnlineTime()
        );
        
        // Update cache
        playerDataCache.put(playerData.getUuid(), playerData);
    }
    
    /**
     * Add player data to cache
     * 
     * @param playerData the player data to add
     */
    public void addPlayerData(PlayerData playerData) {
        playerDataCache.put(playerData.getUuid(), playerData);
        savePlayerData(playerData);
    }
    
    /**
     * Save all cached player data to database
     */
    public void saveAll() {
        for (PlayerData playerData : playerDataCache.values()) {
            savePlayerData(playerData);
        }
    }
    
    /**
     * Clear player data cache
     */
    public void clearCache() {
        playerDataCache.clear();
    }
}

