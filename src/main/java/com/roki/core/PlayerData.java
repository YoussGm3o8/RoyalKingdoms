package com.roki.core;

import cn.nukkit.level.Location;
import cn.nukkit.Player;
import java.time.Instant;

public class PlayerData {
    private final String uuid;
    private final String name;
    private String faction;
    private String rank;
    private Location home;
    private Instant lastLogin;
    private long onlineTime;
    
    // New fields for combat logging
    private boolean inCombat = false;
    private long combatTagExpires = 0;
    private String lastAttacker = null;
    
    // Teleportation state
    private boolean teleporting = false;
    private Location teleportLocation = null;
    private int teleportTaskId = -1;
    private String teleportType = null;
    
    // Cooldowns
    private long goldenAppleCooldownUntil = 0;
    private long enchantedGoldenAppleCooldownUntil = 0;
    private long wildCooldownUntil = 0;
    
    // Scoreboards
    private boolean pvpScoreboardEnabled = false;

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
    
    // Combat tag methods
    public boolean isInCombat() {
        // Check if combat has expired
        if (inCombat && System.currentTimeMillis() > combatTagExpires) {
            inCombat = false;
            lastAttacker = null;
        }
        return inCombat;
    }
    
    public void setCombatTag(Player attacker, int durationSeconds) {
        inCombat = true;
        combatTagExpires = System.currentTimeMillis() + (durationSeconds * 1000);
        if (attacker != null) {
            lastAttacker = attacker.getName();
        }
    }
    
    public void removeCombatTag() {
        inCombat = false;
        lastAttacker = null;
    }
    
    public int getRemainingCombatTime() {
        if (!inCombat) return 0;
        long remaining = (combatTagExpires - System.currentTimeMillis()) / 1000;
        return remaining > 0 ? (int)remaining : 0;
    }
    
    public String getLastAttacker() {
        return lastAttacker;
    }
    
    // Teleportation methods
    public boolean isTeleporting() {
        return teleporting;
    }
    
    public void setTeleporting(boolean teleporting, Location location, int taskId, String type) {
        this.teleporting = teleporting;
        this.teleportLocation = location;
        this.teleportTaskId = taskId;
        this.teleportType = type;
    }
    
    public void cancelTeleport() {
        teleporting = false;
        teleportLocation = null;
        teleportType = null;
    }
    
    public Location getTeleportLocation() {
        return teleportLocation;
    }
    
    public int getTeleportTaskId() {
        return teleportTaskId;
    }
    
    public String getTeleportType() {
        return teleportType;
    }
    
    // Cooldown methods
    public boolean canUseGoldenApple() {
        return System.currentTimeMillis() > goldenAppleCooldownUntil;
    }
    
    public void setGoldenAppleCooldown(int seconds) {
        goldenAppleCooldownUntil = System.currentTimeMillis() + (seconds * 1000);
    }
    
    public int getRemainingGoldenAppleCooldown() {
        long remaining = (goldenAppleCooldownUntil - System.currentTimeMillis()) / 1000;
        return remaining > 0 ? (int)remaining : 0;
    }
    
    public boolean canUseEnchantedGoldenApple() {
        return System.currentTimeMillis() > enchantedGoldenAppleCooldownUntil;
    }
    
    public void setEnchantedGoldenAppleCooldown(int seconds) {
        enchantedGoldenAppleCooldownUntil = System.currentTimeMillis() + (seconds * 1000);
    }
    
    public int getRemainingEnchantedGoldenAppleCooldown() {
        long remaining = (enchantedGoldenAppleCooldownUntil - System.currentTimeMillis()) / 1000;
        return remaining > 0 ? (int)remaining : 0;
    }
    
    public boolean canUseWildCommand() {
        return System.currentTimeMillis() > wildCooldownUntil;
    }
    
    public void setWildCooldown(int seconds) {
        wildCooldownUntil = System.currentTimeMillis() + (seconds * 1000);
    }
    
    public int getRemainingWildCooldown() {
        long remaining = (wildCooldownUntil - System.currentTimeMillis()) / 1000;
        return remaining > 0 ? (int)remaining : 0;
    }
    
    // Scoreboard methods
    public boolean isPvpScoreboardEnabled() {
        return pvpScoreboardEnabled;
    }
    
    public void setPvpScoreboardEnabled(boolean enabled) {
        this.pvpScoreboardEnabled = enabled;
    }
}
