package com.roki.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.nukkit.Player;

public class Faction {
    private String name;
    private Map<String, Integer> votes;
    private List<String> factionPlayers; 
    private double factionVaultBalance; // Vault balance for this faction
    private int factionKills; // Kills for this faction

    public Faction(String name, double vaultBalance) {
        this.name = name;
        this.factionPlayers = new ArrayList<>();
        this.factionVaultBalance = vaultBalance;
        this.factionKills = 0;
        this.votes = new HashMap<>();
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

}
