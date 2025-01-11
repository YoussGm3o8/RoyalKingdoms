package com.roki.core.database;

import cn.nukkit.Player;
import cn.nukkit.level.Location;
import com.roki.core.Faction;
import com.roki.core.RoyalKingdomsCore;

import java.util.Map;

public class DataModel {
    public static final String CREATE_FACTIONS_TABLE = "CREATE TABLE IF NOT EXISTS factions (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL)";
    public static final String CREATE_PORTALS_TABLE = "CREATE TABLE IF NOT EXISTS portals (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL, destination VARCHAR(255) NOT NULL)";
    
    private final DatabaseManager db;
    private final RoyalKingdomsCore plugin;
    
    public DataModel(RoyalKingdomsCore plugin) {
        this.plugin = plugin;
        this.db = new DatabaseManager(plugin);
    }
    
    public DatabaseManager getDatabaseManager() {
        return db;
    }
    
    // Player Methods
    public void savePlayerData(Player player, String factionName) {
        db.savePlayer(player, factionName);
    }
    
    public String getPlayerFaction(Player player) {
        return db.getPlayerFaction(player.getUniqueId().toString());
    }
    
    // Faction Methods
    public void saveFaction(Faction faction) {
        db.saveFaction(faction);
    }
    
    public Map<String, Faction> loadAllFactions() {
        return db.loadAllFactions();
    }
    
    // Warp Methods
    public void saveWarp(String name, Location location) {
        db.saveWarp(name, location);
    }
    
    public Location getWarp(String name) {
        return db.getWarp(name);
    }
    
    // Home Methods
    public void saveHome(Player player, Location location) {
        db.saveHome(player.getUniqueId().toString(), location);
    }
    
    public Location getHome(Player player) {
        return db.getHome(player.getUniqueId().toString());
    }
    
    public void deleteHome(Player player) {
        db.deleteHome(player.getUniqueId().toString());
    }
}