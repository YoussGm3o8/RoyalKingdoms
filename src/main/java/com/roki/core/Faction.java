package com.roki.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import cn.nukkit.level.Location;

public class Faction {
    private String name;
    private String Leader;
    private List<String> factionPlayers; 
    private double factionVaultBalance; // Vault balance for this faction
    private int factionKills; // Kills for this faction
    private Location home; // Faction home location
    private Set<String> allies; // List of allied faction names

    public Faction(String name, double vaultBalance, String leader, int kills) {
        this.name = name;
        this.Leader = leader;
        this.factionPlayers = new ArrayList<>();
        this.factionVaultBalance = vaultBalance;
        this.factionKills = kills;
        this.allies = new HashSet<>();
    }

    public boolean containsPlayer(String playerName) {
        return factionPlayers.contains(playerName);
    }

    public void addPlayer(String playerName) {
        factionPlayers.remove(playerName); // Remove player from other factions
        factionPlayers.add(playerName); // Add to this faction
    }

    public void removePlayer(String playerName) {
        factionPlayers.remove(playerName);
    }

    public String getName() {
        return name;
    }

    public List<String> getPlayers() {
        return factionPlayers;
    }

    public int getPlayerCount() {
        return factionPlayers.size();
    }

    public double getVaultBalance() {
        return factionVaultBalance;
    }

    public void setVaultBalance(double balance) {
        factionVaultBalance = balance;
    }

    public void deposit(double amount) {
        if (amount > 0) {
            factionVaultBalance += amount;
        }
    }

    public int getKills() {
        return factionKills;
    }

    public void setKills(int kills) {
        factionKills = kills;
    }
    
    // Add missing method for incrementing kills
    public void incrementKills() {
        factionKills++;
    }

    public String getFactionLeader() {
        return Leader;
    }

    public void setLeader(String newLeader) {
        this.Leader = newLeader;
    }
    
    /**
     * Set the faction home location
     *
     * @param home The home location
     */
    public void setHome(Location home) {
        this.home = home;
    }

    /**
     * Get the faction home location
     *
     * @return The home location
     */
    public Location getHome() {
        return home;
    }
    
    /**
     * Add an ally to this faction
     *
     * @param factionName The name of the faction to ally with
     */
    public void addAlly(String factionName) {
        allies.add(factionName.toLowerCase());
    }
    
    /**
     * Remove an ally from this faction
     *
     * @param factionName The name of the faction to remove as ally
     */
    public void removeAlly(String factionName) {
        allies.remove(factionName.toLowerCase());
    }
    
    /**
     * Check if this faction is allied with another faction
     *
     * @param factionName The name of the faction to check alliance with
     * @return true if the factions are allied, false otherwise
     */
    public boolean isAlliedWith(String factionName) {
        return allies.contains(factionName.toLowerCase());
    }
    
    /**
     * Get all allied faction names
     *
     * @return Set of allied faction names
     */
    public Set<String> getAllies() {
        return allies;
    }
}
