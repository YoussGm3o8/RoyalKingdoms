package com.roki.core;

import cn.nukkit.Player;
import cn.nukkit.level.Location;
import cn.nukkit.level.Level;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PlayerData {
    private final Player player;
    private final File playerDataFile;

    private String faction;
    private Location home;

    public PlayerData(Player player) {
        this.player = player;
        this.playerDataFile = new File("plugins/RoyalKingdomsCore/players/" + player.getName() + ".yml");
        loadPlayerData();
    }

    public void setFaction(String faction) {
        this.faction = faction;
        savePlayerData();
    }

    public String getFaction() {
        return faction;
    }

    public void setHome(Location home) {
        this.home = home;
        savePlayerData();
    }

    public Location getHome() {
        return home;
    }

    public boolean hasHome() {
        return home != null;
    }

    public void removeHome() {
        this.home = null;
        savePlayerData();
    }

    private void savePlayerData() {
        Yaml yaml = new Yaml();
        Map<String, Object> playerData = new HashMap<>();

        // Save faction
        if (faction != null) {
            playerData.put("faction", faction);
        }

        // Save home
        if (home != null) {
            Map<String, Object> homeData = new HashMap<>();
            homeData.put("world", home.getLevel().getName());
            homeData.put("x", home.getX());
            homeData.put("y", home.getY());
            homeData.put("z", home.getZ());
            homeData.put("yaw", home.getYaw());
            homeData.put("pitch", home.getPitch());
            playerData.put("home", homeData);
        }

        // Ensure parent directory exists
        playerDataFile.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(playerDataFile)) {
            yaml.dump(playerData, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadPlayerData() {
        Yaml yaml = new Yaml();
        
        if (playerDataFile.exists()) {
            try (FileReader reader = new FileReader(playerDataFile)) {
                Map<String, Object> playerData = yaml.load(reader);
                
                if (playerData != null) {
                    // Load faction
                    this.faction = (String) playerData.get("faction");

                    // Load home
                    Map<String, Object> homeData = (Map<String, Object>) playerData.get("home");
                    if (homeData != null) {
                        String worldName = (String) homeData.get("world");
                        double x = ((Number) homeData.get("x")).doubleValue();
                        double y = ((Number) homeData.get("y")).doubleValue();
                        double z = ((Number) homeData.get("z")).doubleValue();
                        float yaw = ((Number) homeData.get("yaw")).floatValue();
                        float pitch = ((Number) homeData.get("pitch")).floatValue();

                        Level world = player.getServer().getLevelByName(worldName);
                        if (world != null) {
                            this.home = new Location(x, y, z, pitch, yaw, world);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}