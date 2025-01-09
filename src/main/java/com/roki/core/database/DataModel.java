package com.roki.core.database;

import cn.nukkit.Player;
import cn.nukkit.level.Location;
import com.roki.core.Faction;
import com.roki.core.RoyalKingdomsCore;

import java.util.Map;

public class DataModel {
    private final DatabaseManager db;
    private final RoyalKingdomsCore plugin;
    
    public DataModel(RoyalKingdomsCore plugin) {
        this.plugin = plugin;
        this.db = new DatabaseManager(plugin);
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