package com.roki.core.database;

import java.time.Instant;

import cn.nukkit.level.Location;

public class PlayerDataModel {
    private final String uuid;
    private final String faction;
    private final String rank;
    private final Location home;
    private final Instant lastLogin;
    private final long onlineTime;

    public PlayerDataModel(String uuid, String faction, String rank, Location home , Instant lastLogin, long onlineTime) {
        this.uuid = uuid;
        this.faction = faction;
        this.rank = rank;
        this.home = home;
        this.lastLogin = lastLogin;
        this.onlineTime = onlineTime;
    }

    public String getUuid() {
        return uuid;
    }

    public String getFaction() {
        return faction;
    }

    public Location getHome() {
        return home;
    }

    public Instant getLastLogin() {
        return lastLogin;
    }

    public long getOnlineTime() {
        return onlineTime;
    }

    public String getRank() {
        return rank;
    }
}