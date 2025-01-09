package com.roki.core.database;

import cn.nukkit.Player;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.plugin.Plugin;
import com.roki.core.Faction;
import com.roki.core.RoyalKingdomsCore;

import java.sql.*;
import java.util.*;

public class DatabaseManager {
    private final String url;
    private final Plugin plugin;

    public DatabaseManager(RoyalKingdomsCore plugin) {
        this.plugin = plugin;
        this.url = "jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/database.db";
        initializeTables();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void initializeTables() {
        // Create tables if they don't exist
        String[] tables = {
            // Players table
            """
            CREATE TABLE IF NOT EXISTS players (
                uuid TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                faction_id INTEGER,
                last_login TIMESTAMP,
                FOREIGN KEY (faction_id) REFERENCES factions(id)
            )
            """,
            
            // Factions table
            """
            CREATE TABLE IF NOT EXISTS factions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                color TEXT NOT NULL,
                vault_balance REAL DEFAULT 0,
                kills INTEGER DEFAULT 0,
                leader_uuid TEXT,
                FOREIGN KEY (leader_uuid) REFERENCES players(uuid)
            )
            """,
            
            // Warps table
            """
            CREATE TABLE IF NOT EXISTS warps (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                world TEXT NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL,
                pitch FLOAT NOT NULL
            )
            """,
            
            // Homes table
            """
            CREATE TABLE IF NOT EXISTS homes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                world TEXT NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL,
                pitch FLOAT NOT NULL,
                FOREIGN KEY (player_uuid) REFERENCES players(uuid),
                UNIQUE(player_uuid)
            )
            """
        };

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            for (String table : tables) {
                stmt.execute(table);
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to initialize database tables", e);
        }
    }

    // Player Methods
    public void savePlayer(Player player, String factionName) {
        String sql = """
            INSERT OR REPLACE INTO players (uuid, name, faction_id, last_login)
            VALUES (?, ?, (SELECT id FROM factions WHERE name = ?), ?)
        """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, player.getUniqueId().toString());
            pstmt.setString(2, player.getName());
            pstmt.setString(3, factionName);
            pstmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to save player data", e);
        }
    }

    public String getPlayerFaction(String uuid) {
        String sql = """
            SELECT f.name
            FROM players p
            JOIN factions f ON p.faction_id = f.id
            WHERE p.uuid = ?
        """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to get player faction", e);
        }
        return null;
    }

    // Faction Methods
    public void saveFaction(Faction faction) {
        String sql = """
            INSERT OR REPLACE INTO factions (name, color, vault_balance, kills, leader_uuid)
            VALUES (?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, faction.getName());
            pstmt.setString(2, faction.getColor());
            pstmt.setDouble(3, faction.getVaultBalance());
            pstmt.setInt(4, faction.getKills());
            pstmt.setString(5, faction.getLeader());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to save faction", e);
        }
    }

    public Map<String, Faction> loadAllFactions() {
        Map<String, Faction> factions = new HashMap<>();
        String sql = "SELECT * FROM factions";
        
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Faction faction = new Faction(
                    rs.getString("name"),
                    rs.getString("color")
                );
                faction.setVaultBalance(rs.getDouble("vault_balance"));
                faction.setKills(rs.getInt("kills"));
                faction.setLeader(rs.getString("leader_uuid"));
                
                // Load faction members
                loadFactionMembers(conn, faction);
                
                factions.put(faction.getName().toLowerCase(), faction);
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to load factions", e);
        }
        return factions;
    }

    private void loadFactionMembers(Connection conn, Faction faction) throws SQLException {
        String sql = """
            SELECT name 
            FROM players 
            WHERE faction_id = (SELECT id FROM factions WHERE name = ?)
        """;
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, faction.getName());
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                faction.addPlayer(rs.getString("name"));
            }
        }
    }

    // Warp Methods
    public void saveWarp(String name, Location location) {
        String sql = """
            INSERT OR REPLACE INTO warps (name, world, x, y, z, yaw, pitch)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, location.getLevel().getName());
            pstmt.setDouble(3, location.getX());
            pstmt.setDouble(4, location.getY());
            pstmt.setDouble(5, location.getZ());
            pstmt.setDouble(6, location.getYaw());
            pstmt.setDouble(7, location.getPitch());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to save warp", e);
        }
    }

    public Location getWarp(String name) {
        String sql = "SELECT * FROM warps WHERE name = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return new Location(
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getFloat("pitch"),
                    rs.getFloat("yaw"),
                    plugin.getServer().getLevelByName(rs.getString("world"))
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to get warp", e);
        }
        return null;
    }

    // Home Methods
    public void saveHome(String playerUuid, Location location) {
        String sql = """
            INSERT OR REPLACE INTO homes (player_uuid, world, x, y, z, yaw, pitch)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid);
            pstmt.setString(2, location.getLevel().getName());
            pstmt.setDouble(3, location.getX());
            pstmt.setDouble(4, location.getY());
            pstmt.setDouble(5, location.getZ());
            pstmt.setDouble(6, location.getYaw());
            pstmt.setDouble(7, location.getPitch());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to save home", e);
        }
    }

    public Location getHome(String playerUuid) {
        String sql = "SELECT * FROM homes WHERE player_uuid = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return new Location(
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getFloat("pitch"),
                    rs.getFloat("yaw"),
                    plugin.getServer().getLevelByName(rs.getString("world"))
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to get home", e);
        }
        return null;
    }

    public void deleteHome(String playerUuid) {
        String sql = "DELETE FROM homes WHERE player_uuid = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to delete home", e);
        }
    }
}