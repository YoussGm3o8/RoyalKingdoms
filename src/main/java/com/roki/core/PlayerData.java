package com.roki.core;

import cn.nukkit.level.Location;
import java.time.Instant;

public class PlayerData {
    private final String uuid;
    private final String name;
    private String faction;
    private String rank;
    private Location home;
    private Instant lastLogin;
    private long onlineTime;

    public PlayerData(String uuid, String name, String faction, String rank, Location home, Instant lastLogin, long onlineTime) {
        this.uuid = uuid;
        this.name = name;
        this.faction = faction;
        this.rank = rank;
        this.home = home;
        this.lastLogin = lastLogin;
        this.onlineTime = onlineTime;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getFaction() {
        return faction;
    }

    public void setFaction(String faction) {
        this.faction = faction;
    }

    public String getRank() {
        return rank;
    }

    public void setRank(String rank) {
        this.rank = rank;
    }

    public Location getHome() {
        return home;
    }

    public void setHome(Location home) {
        this.home = home;
    }

    public Instant getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(Instant lastLogin) {
        this.lastLogin = lastLogin;
    }

    public long getOnlineTime() {
        return onlineTime;
    }

    public void setOnlineTime(long onlineTime) {
        this.onlineTime = onlineTime;
    }

    public boolean isLeader() {
        return "Leader".equalsIgnoreCase(rank);
    }

    public boolean isOfficer() {
        return "Officer".equalsIgnoreCase(rank);
    }
}
