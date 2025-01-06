package com.roki.core;

import cn.nukkit.Player;
import cn.nukkit.level.Location;
import cn.nukkit.level.Level;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class PlayerData {
    private final Player player;
    private final File playerDataFile;

    private String faction;
    private Location home;
    private boolean isFactionLeader;
    private Instant lastLogin;
    private long onlineTime; // in seconds

    public PlayerData(Player player) {
        this.player = player;
        this.playerDataFile = new File("plugins/RoyalKingdomsCore/players/" + player.getName() + ".yml");
        loadPlayerData();
    }

    public void setFaction(String faction) {
        if (faction == null || faction.trim().isEmpty()) {
            System.out.println("Attempted to set an invalid faction for " + player.getName());
            return;
        }
        this.faction = faction;
        System.out.println("Faction for " + player.getName() + " set to " + faction);
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

    public boolean isFactionLeader() {
        return isFactionLeader;
    }

    public void setFactionLeader(boolean isFactionLeader) {
        this.isFactionLeader = isFactionLeader;
        savePlayerData();
    }

    public void updateLastLogin() {
        this.lastLogin = Instant.now();
        savePlayerData();
    }

    public void addOnlineTime(long seconds) {
        this.onlineTime += seconds;
        savePlayerData();
    }

    public void checkFactionLeaderStatus() {
        Instant tenDaysAgo = Instant.now().minusSeconds(10 * 24 * 60 * 60);
        if (lastLogin != null && lastLogin.isBefore(tenDaysAgo) && onlineTime < 2 * 60 * 60) {
            setFactionLeader(false);
        }
    }

    public void savePlayerData() {
        Yaml yaml = new Yaml();
        Map<String, Object> playerData = new HashMap<>();

        // Save faction
        if (faction != null && !faction.isEmpty()) {
            playerData.put("faction", faction);
        } else {
            System.out.println("Faction is null or empty for " + player.getName());
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

        // Save faction leader status
        playerData.put("isFactionLeader", isFactionLeader);

        // Save last login and online time
        if (lastLogin != null) {
            playerData.put("lastLogin", lastLogin.toString());
        }
        playerData.put("onlineTime", onlineTime);

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

                    // Load faction leader status
                    this.isFactionLeader = (boolean) playerData.getOrDefault("isFactionLeader", false);

                    // Load last login and online time
                    String lastLoginStr = (String) playerData.get("lastLogin");
                    if (lastLoginStr != null) {
                        this.lastLogin = Instant.parse(lastLoginStr);
                    }
                    this.onlineTime = ((Number) playerData.getOrDefault("onlineTime", 0)).longValue();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
