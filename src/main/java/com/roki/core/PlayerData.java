package com.roki.core;

import cn.nukkit.Player;
import cn.nukkit.level.Location;
import com.roki.core.database.DatabaseManager;
import com.roki.core.database.PlayerDataModel;
import java.time.Instant;

public class PlayerData {
    private final Player player;
    private final DatabaseManager dbManager;

    private String faction;
    private String rank;
    private Location home;
    private Instant lastLogin;
    private long onlineTime; // in seconds

    public PlayerData(Player player, DatabaseManager dbManager) {
        this.player = player;
        this.dbManager = dbManager;
        loadPlayerData();
    }

    public void setFaction(String faction) {
        this.faction = faction;
        savePlayerData();
    }

    public String getFaction() {
        return faction;
    }

    public void setRank(String rank) {
        this.rank = rank;
        savePlayerData();
    }

    public String getRank() {
        return rank;
    }

    public boolean isLeader() {
        return "Leader".equals(rank);
    }

    public boolean isOfficer() {
        return "Officer".equals(rank);
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

    public void updateLastLogin() {
        this.lastLogin = Instant.now();
        savePlayerData();
    }

    public void addOnlineTime(long seconds) {
        this.onlineTime += seconds;
        savePlayerData();
    }

    public void savePlayerData() {
        dbManager.savePlayerData(player.getUniqueId().toString(), player.getName(), faction, rank, home, lastLogin, onlineTime);
    }

    private void loadPlayerData() {
        PlayerDataModel data = dbManager.loadPlayerData(player.getUniqueId().toString());
        if (data != null) {
            this.faction = data.getFaction();
            this.rank = data.getRank();
            this.home = data.getHome();
            this.lastLogin = data.getLastLogin();
            this.onlineTime = data.getOnlineTime();
        }
    }
}
