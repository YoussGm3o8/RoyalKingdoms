package com.roki.core;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

// Faction class as before
public class Faction {
    private String name;
    private String color;
    private Map<String, List<String>> factionPlayers; 
    private Map<String, Double> factionVaultBalances; // New map for faction-specific vault balances
    private Map<String, Integer> factionKills; // New map for faction-specific kills
    private final File factionDataFile;

    public Faction(String name, String color) {
        this.name = name;
        this.color = color;
        this.factionPlayers = new HashMap<>();
        this.factionVaultBalances = new HashMap<>();
        this.factionKills = new HashMap<>();
        this.factionDataFile = new File("plugins/RoyalKingdomsCore/faction_data.yml");

        loadFactionData();
    }
    
    public boolean containsPlayer(String playerName) {
        return factionPlayers.getOrDefault(this.name, new ArrayList<>()).contains(playerName);
    }
    
    public void addPlayer(String playerName) {
        // Remove player from any existing faction first
        for (List<String> factionsPlayerList : factionPlayers.values()) {
            factionsPlayerList.remove(playerName);
        }
        
        // Add player to this specific faction
        if (!factionPlayers.containsKey(this.name)) {
            factionPlayers.put(this.name, new ArrayList<>());
        }
        
        // Only add if not already in the list
        if (!factionPlayers.get(this.name).contains(playerName)) {
            factionPlayers.get(this.name).add(playerName);
        }
        
        saveFactionData();
    }

    public void removePlayer(String playerName) {
        if (factionPlayers.containsKey(this.name)) {
            factionPlayers.get(this.name).remove(playerName);
        }
        saveFactionData(); // Save data after removing player
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public List<String> getPlayers() {
        return factionPlayers.getOrDefault(this.name, new ArrayList<>());
    }

    public int getPlayerCount() {
        return factionPlayers.getOrDefault(this.name, new ArrayList<>()).size();
    }

    public double getVaultBalance() {
        return factionVaultBalances.getOrDefault(this.name, 0.0);
    }

    public void deposit(double amount) {
        if (amount > 0) {
            double currentBalance = factionVaultBalances.getOrDefault(this.name, 0.0);
            factionVaultBalances.put(this.name, currentBalance + amount);
            saveFactionData();
        }
    }

    public int getKills() {
        return factionKills.getOrDefault(this.name, 0);
    }

    public void addKills(int amount) {
        int currentKills = factionKills.getOrDefault(this.name, 0);
        factionKills.put(this.name, currentKills + amount);
        saveFactionData();
    }

    // Method to save the faction data (including players and vault balance)
    private void saveFactionData() {
        Yaml yaml = new Yaml();
        Map<String, Object> factionData = new HashMap<>();
        factionData.put("factionVaultBalances", factionVaultBalances);
        factionData.put("factionKills", factionKills);
        factionData.put("factionPlayers", factionPlayers);

        try (FileWriter writer = new FileWriter(factionDataFile)) {
            yaml.dump(factionData, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to load the faction data (including players and vault balance)
    @SuppressWarnings("unchecked") void loadFactionData() {
    Yaml yaml = new Yaml();
    if (factionDataFile.exists()) {
        try (FileReader reader = new FileReader(factionDataFile)) {
            Map<String, Object> factionData = yaml.load(reader);
            if (factionData != null) {
                factionVaultBalances = (Map<String, Double>) factionData.getOrDefault("factionVaultBalances", new HashMap<>());
                factionKills = (Map<String, Integer>) factionData.getOrDefault("factionKills", new HashMap<>());
                factionPlayers = (Map<String, List<String>>) factionData.getOrDefault("factionPlayers", new HashMap<>());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
}
