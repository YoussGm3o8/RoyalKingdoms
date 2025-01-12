package com.roki.core;

import java.util.ArrayList;
import java.util.List;


public class Faction {
    private String name;
    private String Leader;
    private List<String> factionPlayers; 
    private double factionVaultBalance; // Vault balance for this faction
    private int factionKills; // Kills for this faction

    public Faction(String name, double vaultBalance, String leader, int kills) {
        this.name = name;
        this.Leader = leader;
        this.factionPlayers = new ArrayList<>();
        this.factionVaultBalance = vaultBalance;
        this.factionKills = kills;
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

    public void addKills(int amount) {
        factionKills += amount;
    }

    public String getFactionLeader() {
        return Leader;
    }

}
