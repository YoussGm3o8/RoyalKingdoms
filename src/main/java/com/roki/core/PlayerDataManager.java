package com.roki.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import cn.nukkit.Player;

public class PlayerDataManager {
    private final Map<String, PlayerData> cache = new ConcurrentHashMap<>();
    
    public PlayerData getPlayerData(Player player) {
        if (!cache.containsKey(player.getName())) {
            cache.put(player.getName(), new PlayerData(player));
        }
        return cache.get(player.getName());
    }
    
    public void saveAll() {
        for (PlayerData data : cache.values()) {
            data.savePlayerData(); // Need to make savePlayerData public
        }
    }
    
    public void saveAndRemove(Player player) {
        PlayerData data = cache.remove(player.getName());
        if (data != null) {
            data.savePlayerData();
        }
    }

    public void clearCache() {
        saveAll();
        cache.clear();
    }
}

