package com.roki.core.chunkProtection;

import cn.nukkit.level.Position;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.Level;
import java.util.*;

public class ProtectedChunkData {
    private String factionName;
    private String worldName;
    private int chunkX;
    private int chunkZ;
    private boolean protectionActive = true; // Default to active
    private Set<WarningType> sentWarnings = EnumSet.noneOf(WarningType.class);
    private int shieldHealth = 64; // Default shield health

    public enum WarningType {
        SEVEN_DAYS,
        THREE_DAYS,
        ONE_DAY
    }

    public boolean hasWarningBeenSent(WarningType warningType) {
        return sentWarnings.contains(warningType);
    }

    public void markWarningSent(WarningType warningType) {
        sentWarnings.add(warningType);
    }

    public void resetWarnings() {
        sentWarnings.clear();
    }

    public ProtectedChunkData(String factionName, String worldName, int chunkX, int chunkZ) {
        this.factionName = factionName;
        this.worldName = worldName;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public String getFactionName() {
        return factionName;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public boolean isProtectionActive() {
        return protectionActive;
    }

    public void setProtectionActive(boolean active) {
        this.protectionActive = active;
    }

    public int getShieldHealth() {
        return shieldHealth;
    }

    public void setShieldHealth(int health) {
        this.shieldHealth = health;
    }

    public boolean isSameChunk(int chunkX, int chunkZ, String worldName) {
        return this.chunkX == chunkX && this.chunkZ == chunkZ && this.worldName.equals(worldName);
    }

    public BaseFullChunk getChunk(Level level) {
        return level.getChunk(chunkX, chunkZ);
    }

    public Level getLevel() {
        return cn.nukkit.Server.getInstance().getLevelByName(worldName);
    }
}