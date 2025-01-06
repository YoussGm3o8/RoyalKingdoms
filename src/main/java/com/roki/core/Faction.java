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

import cn.nukkit.Player;

public class Faction {
    private String name;
    private String leader;
    private Player factionLeader;
    private String color;
    private Map<String, Integer> votes;
    private List<String> factionPlayers; 
    private double factionVaultBalance; // Vault balance for this faction
    private int factionKills; // Kills for this faction
    private final File factionDataFile;

    public Faction(String name, String color) {
        this.name = name;
        this.color = color;
        this.factionPlayers = new ArrayList<>();
        this.factionVaultBalance = 0.0;
        this.factionKills = 0;
        this.votes = new HashMap<>();
        this.factionDataFile = new File("plugins/RoyalKingdomsCore/factions/" + name + ".yml");

        loadFactionData();
    }

    public boolean containsPlayer(String playerName) {
        return factionPlayers.contains(playerName);
    }

    public void addPlayer(String playerName) {
        factionPlayers.remove(playerName); // Remove player from other factions
        factionPlayers.add(playerName); // Add to this faction
        saveFactionData();
    }

    public void removePlayer(String playerName) {
        factionPlayers.remove(playerName);
        saveFactionData();
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
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

    public void deposit(double amount) {
        if (amount > 0) {
            factionVaultBalance += amount;
            saveFactionData();
        }
    }

    public int getKills() {
        return factionKills;
    }

    public void addKills(int amount) {
        factionKills += amount;
        saveFactionData();
    }

    public void setLeader(String leader) {
        this.leader = leader;
        saveFactionData();
    }

    public String getLeader() {
        return leader;
    }

    public void resetSeasonData() {
        leader = null;
        votes.clear();
        factionVaultBalance = 0.0;
        factionKills = 0;
        saveFactionData();
    }

    public void saveFactionData() {
        Yaml yaml = new Yaml();
        Map<String, Object> factionData = new HashMap<>();
        factionData.put("name", name);
        factionData.put("color", color);
        factionData.put("leader", leader);
        factionData.put("factionPlayers", factionPlayers);
        factionData.put("factionVaultBalance", factionVaultBalance);
        factionData.put("factionKills", factionKills);
        factionData.put("votes", votes);

        try (FileWriter writer = new FileWriter(factionDataFile)) {
            yaml.dump(factionData, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadFactionData() {
        Yaml yaml = new Yaml();
        if (factionDataFile.exists()) {
            try (FileReader reader = new FileReader(factionDataFile)) {
                Map<String, Object> factionData = yaml.load(reader);
                if (factionData != null) {
                    name = (String) factionData.getOrDefault("name", name);
                    color = (String) factionData.getOrDefault("color", color);
                    leader = (String) factionData.get("leader");
                    factionPlayers = (List<String>) factionData.getOrDefault("factionPlayers", new ArrayList<>());
                    factionVaultBalance = (double) factionData.getOrDefault("factionVaultBalance", 0.0);
                    factionKills = (int) factionData.getOrDefault("factionKills", 0);
                    votes = (Map<String, Integer>) factionData.getOrDefault("votes", new HashMap<>());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
