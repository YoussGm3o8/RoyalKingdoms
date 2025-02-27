package com.roki.core.database;

import cn.nukkit.Player;
import cn.nukkit.level.Location;
import com.roki.core.Faction;
import com.roki.core.RoyalKingdomsCore;
import com.roki.core.chunkProtection.ProtectedChunkData;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class DatabaseManager {
    private final String url;
    private final RoyalKingdomsCore plugin;

    public DatabaseManager(RoyalKingdomsCore plugin) {
        this.plugin = plugin;
        this.url = "jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/database.db";
        loadDriver();
        initializeTables();
    }

    private void loadDriver() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().error("Failed to load SQLite JDBC driver", e);
        }
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void initializeTables() {
        // Create tables if they don't exist
        String[] tables = {
            // Players table
            "CREATE TABLE IF NOT EXISTS players (\n" +
                "    uuid TEXT PRIMARY KEY,\n" +
                "    name TEXT NOT NULL,\n" +
                "    faction_id INTEGER,\n" +
                "    rank TEXT NOT NULL DEFAULT 'Member',\n" +
                "    last_login TIMESTAMP,\n" +
                "    online_time INTEGER DEFAULT 0,\n" +
                "    FOREIGN KEY (faction_id) REFERENCES factions(id)\n" +
                ")",

            // Factions table
            "CREATE TABLE IF NOT EXISTS factions (\n" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                "    name TEXT NOT NULL UNIQUE,\n" +
                "    leader TEXT NOT NULL,\n" +
                "    vault_balance REAL DEFAULT 0,\n" +
                "    kills INTEGER DEFAULT 0\n" +
                ")",

            // Warps table
            "CREATE TABLE IF NOT EXISTS warps (\n" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                "    name TEXT NOT NULL UNIQUE,\n" +
                "    world TEXT NOT NULL,\n" +
                "    x DOUBLE NOT NULL,\n" +
                "    y DOUBLE NOT NULL,\n" +
                "    z DOUBLE NOT NULL,\n" +
                "    yaw FLOAT NOT NULL,\n" +
                "    pitch FLOAT NOT NULL\n" +
                ")",

            // Homes table
            "CREATE TABLE IF NOT EXISTS homes (\n" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                "    player_uuid TEXT NOT NULL,\n" +
                "    world TEXT NOT NULL,\n" +
                "    x DOUBLE NOT NULL,\n" +
                "    y DOUBLE NOT NULL,\n" +
                "    z DOUBLE NOT NULL,\n" +
                "    yaw FLOAT NOT NULL,\n" +
                "    pitch FLOAT NOT NULL,\n" +
                "    FOREIGN KEY (player_uuid) REFERENCES players(uuid),\n" +
                "    UNIQUE(player_uuid)\n" +
                ")",

            // Portals table
            "CREATE TABLE IF NOT EXISTS portals (\n" +
                "    name TEXT PRIMARY KEY,\n" +
                "    world TEXT NOT NULL,\n" +
                "    x1 DOUBLE NOT NULL,\n" +
                "    y1 DOUBLE NOT NULL,\n" +
                "    z1 DOUBLE NOT NULL,\n" +
                "    x2 DOUBLE NOT NULL,\n" +
                "    y2 DOUBLE NOT NULL,\n" +
                "    z2 DOUBLE NOT NULL,\n" +
                "    color TEXT NOT NULL,\n" +
                "    command TEXT\n" +
                ")",

            // Allies table
            "CREATE TABLE IF NOT EXISTS allies (\n" +
                "    faction1 TEXT NOT NULL,\n" +
                "    faction2 TEXT NOT NULL,\n" +
                "    PRIMARY KEY (faction1, faction2)\n" +
                ")",

            // Protected chunks table
            "CREATE TABLE IF NOT EXISTS protected_chunks (\n" +
                "    faction_name TEXT NOT NULL,\n" +
                "    world_name TEXT NOT NULL,\n" +
                "    chunk_x INTEGER NOT NULL,\n" +
                "    chunk_z INTEGER NOT NULL,\n" +
                "    shield_health INTEGER NOT NULL,\n" +
                "    PRIMARY KEY (world_name, chunk_x, chunk_z)\n" +
                ")",

            // Member permissions table
            "CREATE TABLE IF NOT EXISTS member_permissions (\n" +
                "    faction_name TEXT NOT NULL,\n" +
                "    permission TEXT NOT NULL,\n" +
                "    value BOOLEAN NOT NULL,\n" +
                "    PRIMARY KEY (faction_name, permission)\n" +
                ")",

            // Ally permissions table
            "CREATE TABLE IF NOT EXISTS ally_permissions (\n" +
                "    faction_name TEXT NOT NULL,\n" +
                "    permission TEXT NOT NULL,\n" +
                "    value BOOLEAN NOT NULL,\n" +
                "    PRIMARY KEY (faction_name, permission)\n" +
                ")",

            // Shield reactor table
            "CREATE TABLE IF NOT EXISTS shield_reactor (\n" +
                "    faction_name TEXT PRIMARY KEY,\n" +
                "    health INTEGER NOT NULL DEFAULT 0,\n" +
                "    last_damage_time BIGINT\n" +
                ")"
        };

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            for (String table : tables) {
                stmt.execute(table);
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to initialize database tables", e);
        }

        // Add a column to track dragon ownership
        String addDragonColumn = "ALTER TABLE players ADD COLUMN has_dragon BOOLEAN DEFAULT FALSE";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(addDragonColumn);
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column name")) {
                plugin.getLogger().error("Failed to add has_dragon column to players table", e);
            }
        }
    }

    public void executeUpdate(String sql, Object... params) {
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Player Methods
    public void savePlayerData(String uuid, String name, String faction, String rank, Location home, Instant lastLogin, long onlineTime) {
        String sql = "INSERT OR REPLACE INTO players (uuid, name, faction_id, rank, last_login, online_time)\n" +
            "VALUES (?, ?, (SELECT id FROM factions WHERE name = ?), ?, ?, ?)";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            pstmt.setString(2, name);
            pstmt.setString(3, faction);
            pstmt.setString(4, rank);
            pstmt.setTimestamp(5, lastLogin != null ? Timestamp.from(lastLogin) : null);
            pstmt.setLong(6, onlineTime);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to save player data", e);
        }

        if (home != null) {
            saveHome(uuid, home);
        }
    }

    public PlayerDataModel loadPlayerData(String uuid) {
        String sql = "SELECT p.uuid, p.name, f.name as faction, p.rank, h.world, h.x, h.y, h.z, h.yaw, h.pitch, p.last_login, p.online_time\n" +
            "FROM players p\n" +
            "LEFT JOIN factions f ON p.faction_id = f.id\n" +
            "LEFT JOIN homes h ON p.uuid = h.player_uuid\n" +
            "WHERE p.uuid = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                String playerName = rs.getString("name");
                String faction = rs.getString("faction");
                String rank = rs.getString("rank");
                Location home = null;
                if (rs.getString("world") != null) {
                    home = new Location(
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch"),
                        plugin.getServer().getLevelByName(rs.getString("world"))
                    );
                }
                Instant lastLogin = rs.getTimestamp("last_login") != null ? rs.getTimestamp("last_login").toInstant() : null;
                long onlineTime = rs.getLong("online_time");

                return new PlayerDataModel(uuid, playerName, faction, rank, home, lastLogin, onlineTime);
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to load player data", e);
        }
        return null;
    }

    public void savePlayer(Player player, String factionName, String rank) {
        if (player.getName() == null) {
            plugin.getLogger().error("Player name is null for UUID: " + player.getUniqueId().toString());
            return;
        }

        String sql = "INSERT OR REPLACE INTO players (uuid, name, faction_id, rank, last_login)\n" +
            "VALUES (?, ?, (SELECT id FROM factions WHERE name = ?), ?, ?)";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, player.getUniqueId().toString());
            pstmt.setString(2, player.getName());
            pstmt.setString(3, factionName);
            pstmt.setString(4, rank);
            pstmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to save player data", e);
        }
    }

    public String getPlayerFaction(String uuid) {
        String sql = "SELECT f.name\n" +
            "FROM players p\n" +
            "JOIN factions f ON p.faction_id = f.id\n" +
            "WHERE p.uuid = ?";
        
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
        String sql = "UPDATE factions\n" +
            "SET leader = ?, vault_balance = ?, kills = ?\n" +
            "WHERE name = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, faction.getFactionLeader());
            pstmt.setDouble(2, faction.getVaultBalance());
            pstmt.setInt(3, faction.getKills());
            pstmt.setString(4, faction.getName());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to save faction", e);
        }
    }

    public void createFaction(Faction faction) {
        String sql = "INSERT INTO factions (name, leader, vault_balance, kills)\n" +
            "VALUES (?, ?, ?, ?)";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, faction.getName());
            pstmt.setString(2, faction.getFactionLeader());
            pstmt.setDouble(3, faction.getVaultBalance());
            pstmt.setInt(4, faction.getKills());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to create faction", e);
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
                    rs.getDouble("vault_balance"),
                    rs.getString("leader"),
                    rs.getInt("kills")
                );
                faction.setVaultBalance(rs.getDouble("vault_balance"));
                faction.setKills(rs.getInt("kills"));
                
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
        String sql = "SELECT name \n" +
            "FROM players \n" +
            "WHERE faction_id = (SELECT id FROM factions WHERE name = ?)";
        
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
        String sql = "INSERT OR REPLACE INTO warps (name, world, x, y, z, yaw, pitch)\n" +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
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

    public boolean deleteWarp(String warpName) {
        String sql = "DELETE FROM warps WHERE name = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, warpName);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to delete warp", e);
            return false;
        }
    }

    public List<String> getAllWarps() {
        List<String> warps = new ArrayList<>();
        String sql = "SELECT name FROM warps ORDER BY name";
        
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                warps.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to get all warps", e);
        }
        return warps;
    }

    // Home Methods
    public void saveHome(String playerUuid, Location location) {
        String sql = "INSERT OR REPLACE INTO homes (player_uuid, world, x, y, z, yaw, pitch)\n" +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
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

    public boolean factionExists(String factionName) {
        String sql = "SELECT 1 FROM factions WHERE LOWER(name) = LOWER(?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, factionName);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to check faction existence", e);
            return false;
        }
    }

    public Faction getFactionInfo(String factionName) {
        String sql = "SELECT name, leader, vault_balance, kills FROM factions WHERE LOWER(name) = LOWER(?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, factionName);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return new Faction(
                    rs.getString("name"),
                    rs.getDouble("vault_balance"),
                    rs.getString("leader"),
                    rs.getInt("kills")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to get faction info", e);
        }
        return null;
    }

    public List<String> getFactionPlayers(String factionName) {
        List<String> players = new ArrayList<>();
        String sql = "SELECT p.name\n" +
            "FROM players p\n" +
            "JOIN factions f ON p.faction_id = f.id\n" +
            "WHERE LOWER(f.name) = LOWER(?)\n" +
            "ORDER BY p.name";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, factionName);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                players.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to get faction players", e);
        }
        return players;
    }

    public List<String> getAllFactionNames() {
        List<String> factions = new ArrayList<>();
        String sql = "SELECT name FROM factions ORDER BY name";
        
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                factions.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to get all faction names", e);
        }
        return factions;
    }

    public void removePlayerFromFaction(String uuid) {
        String sql = "UPDATE players SET faction_id = NULL WHERE uuid = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to remove player from faction", e);
        }
    }

    public String getFactionWithLeastPlayers() {
        String sql = "SELECT f.name, COUNT(p.uuid) as player_count\n" +
            "FROM factions f\n" +
            "LEFT JOIN players p ON f.id = p.faction_id\n" +
            "GROUP BY f.id\n" +
            "ORDER BY player_count ASC\n" +
            "LIMIT 1";
        
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getString("name");
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to get faction with least players", e);
        }
        return null;
    }

    public void addPlayerToFaction(String uuid, String playerName, String factionName, String rank) {
        if (playerName == null) {
            plugin.getLogger().error("Player name is null for UUID: " + uuid);
            return;
        }

        String sql = "UPDATE players \n" +
            "SET faction_id = (SELECT id FROM factions WHERE name = ?),\n" +
            "    name = ?,\n" +
            "    rank = ?,\n" +
            "    last_login = ?\n" +
            "WHERE uuid = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, factionName);
            pstmt.setString(2, playerName);
            pstmt.setString(3, rank);
            pstmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            pstmt.setString(5, uuid);
            
            if (pstmt.executeUpdate() == 0) {
                // Player doesn't exist, insert new record
                sql = "INSERT INTO players (uuid, name, faction_id, rank, last_login)\n" +
                    "VALUES (?, ?, (SELECT id FROM factions WHERE name = ?), ?, ?)";
                try (PreparedStatement insertStmt = conn.prepareStatement(sql)) {
                    insertStmt.setString(1, uuid);
                    insertStmt.setString(2, playerName);
                    insertStmt.setString(3, factionName);
                    insertStmt.setString(4, rank);
                    insertStmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
                    insertStmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to add player to faction", e);
        }
    }

    public Double getFactionBalance(String factionName) {
        String sql = "SELECT vault_balance FROM factions WHERE LOWER(name) = LOWER(?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, factionName);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getDouble("vault_balance");
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to get faction balance", e);
        }
        return null;
    }

    public void addToFactionBalance(String factionName, double amount) {
        String sql = "UPDATE factions SET vault_balance = vault_balance + ? WHERE LOWER(name) = LOWER(?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setString(2, factionName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to update faction balance", e);
        }
    }

    public Map<String, Double> getTopFactionsByBalance() {
        Map<String, Double> topFactions = new LinkedHashMap<>();
        String sql = "SELECT name, vault_balance FROM factions ORDER BY vault_balance DESC";
        
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                topFactions.put(rs.getString("name"), rs.getDouble("vault_balance"));
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to get top factions by balance", e);
        }
        return topFactions;
    }

    public Map<String, Integer> getTopFactionsByKills() {
        Map<String, Integer> topFactions = new LinkedHashMap<>();
        String sql = "SELECT name, kills FROM factions ORDER BY kills DESC";
        
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                topFactions.put(rs.getString("name"), rs.getInt("kills"));
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to get top factions by kills", e);
        }
        return topFactions;
    }

    public void deleteFactionIfEmpty(String factionName) {
        String sql = "DELETE FROM factions\n" +
            "WHERE name = ?\n" +
            "AND vault_balance = 0\n" +
            "AND (SELECT COUNT(*) FROM players WHERE faction_id = (SELECT id FROM factions WHERE name = ?)) = 0";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, factionName);
            pstmt.setString(2, factionName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to delete faction if empty", e);
        }
    }

    public void deleteFaction(String factionName) {
        String sql = "DELETE FROM factions WHERE name = ?";
        try (Connection conn = connect();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, factionName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to delete faction", e);
        }
    }

    public boolean isAlly(String factionName, String requestedFaction) {
        String sql = "SELECT COUNT(*) FROM allies WHERE (faction1 = ? AND faction2 = ?) OR (faction1 = ? AND faction2 = ?)";
        int count = 0;
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, factionName);
            pstmt.setString(2, requestedFaction);
            pstmt.setString(3, requestedFaction);
            pstmt.setString(4, factionName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to check if factions are allies", e);
        }
        return count > 0;
    }

    public void addAlly(String faction1, String faction2) {
        String sql = "INSERT INTO allies (faction1, faction2) VALUES (?, ?)";
        executeUpdate(sql, faction1, faction2);
    }

    public void removeAlly(String faction1, String faction2) {
        String sql = "DELETE FROM allies WHERE (faction1 = ? AND faction2 = ?) OR (faction1 = ? AND faction2 = ?)";
        executeUpdate(sql, faction1, faction2, faction2, faction1);
    }

    public List<String> getAllies(String factionName) {
        List<String> allies = new ArrayList<>();
        String sql = "SELECT faction2 FROM allies WHERE faction1 = ? UNION SELECT faction1 FROM allies WHERE faction2 = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, factionName);
            pstmt.setString(2, factionName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                allies.add(rs.getString(1));
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to get allies for faction " + factionName, e);
        }
        return allies;
    }

    public void saveOfflineMessage(String playerName, String message) {
        String sql = "INSERT INTO offline_messages (player_name, message) VALUES (?, ?)";
        executeUpdate(sql, playerName, message);
    }

    public void saveAllProtectedChunks(List<ProtectedChunkData> chunks) {
        String deleteSQL = "DELETE FROM protected_chunks";
        String insertSQL = "INSERT INTO protected_chunks (faction_name, world_name, chunk_x, chunk_z, shield_health) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = connect();
             Statement deleteStmt = conn.createStatement();
             PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

            // Clear existing data
            deleteStmt.executeUpdate(deleteSQL);

            // Insert new data
            for (ProtectedChunkData chunk : chunks) {
                insertStmt.setString(1, chunk.getFactionName());
                insertStmt.setString(2, chunk.getWorldName());
                insertStmt.setInt(3, chunk.getChunkX());
                insertStmt.setInt(4, chunk.getChunkZ());
                insertStmt.setInt(5, chunk.getShieldHealth());
                insertStmt.addBatch();
            }
            insertStmt.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<ProtectedChunkData> loadAllProtectedChunks() {
        List<ProtectedChunkData> chunks = new ArrayList<>();
        String selectSQL = "SELECT faction_name, world_name, chunk_x, chunk_z, shield_health FROM protected_chunks";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSQL)) {

            while (rs.next()) {
                String factionName = rs.getString("faction_name");
                String worldName = rs.getString("world_name");
                int chunkX = rs.getInt("chunk_x");
                int chunkZ = rs.getInt("chunk_z");
                int shieldHealth = rs.getInt("shield_health");

                ProtectedChunkData chunk = new ProtectedChunkData(factionName, worldName, chunkX, chunkZ);
                chunk.setShieldHealth(shieldHealth);
                chunks.add(chunk);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return chunks;
    }

    public void saveMemberPermissions(Map<String, Map<String, Boolean>> permissions) {
        String deleteSQL = "DELETE FROM member_permissions";
        String insertSQL = "INSERT INTO member_permissions (faction_name, permission, value) VALUES (?, ?, ?)";

        try (Connection conn = connect();
             Statement deleteStmt = conn.createStatement();
             PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

            // Clear existing data
            deleteStmt.executeUpdate(deleteSQL);

            // Insert new data
            for (Map.Entry<String, Map<String, Boolean>> factionEntry : permissions.entrySet()) {
                String factionName = factionEntry.getKey();
                for (Map.Entry<String, Boolean> permissionEntry : factionEntry.getValue().entrySet()) {
                    insertStmt.setString(1, factionName);
                    insertStmt.setString(2, permissionEntry.getKey());
                    insertStmt.setBoolean(3, permissionEntry.getValue());
                    insertStmt.addBatch();
                }
            }
            insertStmt.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to save member permissions", e);
        }
    }

    public Map<String, Map<String, Boolean>> loadMemberPermissions() {
        Map<String, Map<String, Boolean>> permissions = new HashMap<>();
        String selectSQL = "SELECT faction_name, permission, value FROM member_permissions";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSQL)) {

            while (rs.next()) {
                String factionName = rs.getString("faction_name");
                String permission = rs.getString("permission");
                boolean value = rs.getBoolean("value");

                permissions.computeIfAbsent(factionName, k -> new HashMap<>()).put(permission, value);
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to load member permissions", e);
        }
        return permissions;
    }

    public void saveAllyPermissions(Map<String, Map<String, Boolean>> permissions) {
        String deleteSQL = "DELETE FROM ally_permissions";
        String insertSQL = "INSERT INTO ally_permissions (faction_name, permission, value) VALUES (?, ?, ?)";

        try (Connection conn = connect();
             Statement deleteStmt = conn.createStatement();
             PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

            // Clear existing data
            deleteStmt.executeUpdate(deleteSQL);

            // Insert new data
            for (Map.Entry<String, Map<String, Boolean>> factionEntry : permissions.entrySet()) {
                String factionName = factionEntry.getKey();
                for (Map.Entry<String, Boolean> permissionEntry : factionEntry.getValue().entrySet()) {
                    insertStmt.setString(1, factionName);
                    insertStmt.setString(2, permissionEntry.getKey());
                    insertStmt.setBoolean(3, permissionEntry.getValue());
                    insertStmt.addBatch();
                }
            }
            insertStmt.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Map<String, Boolean>> loadAllyPermissions() {
        Map<String, Map<String, Boolean>> permissions = new HashMap<>();
        String selectSQL = "SELECT faction_name, permission, value FROM ally_permissions";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSQL)) {

            while (rs.next()) {
                String factionName = rs.getString("faction_name");
                String permission = rs.getString("permission");
                boolean value = rs.getBoolean("value");

                permissions.computeIfAbsent(factionName, k -> new HashMap<>()).put(permission, value);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return permissions;
    }

    public void saveShieldReactorData(Map<String, Integer> reactorHealth, Map<String, Long> lastDamageTime) {
        String sql = "INSERT OR REPLACE INTO shield_reactor (faction_name, health, last_damage_time)\n" +
            "VALUES (?, ?, ?)";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            for (Map.Entry<String, Integer> entry : reactorHealth.entrySet()) {
                String factionName = entry.getKey();
                int health = entry.getValue();
                Long damageTime = lastDamageTime.get(factionName);

                pstmt.setString(1, factionName);
                pstmt.setInt(2, health);
                pstmt.setObject(3, damageTime);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to save shield reactor data", e);
        }
    }

    public Map<String, Integer> loadShieldReactorHealth() {
        Map<String, Integer> reactorHealth = new HashMap<>();
        String sql = "SELECT faction_name, health FROM shield_reactor";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                reactorHealth.put(rs.getString("faction_name"), rs.getInt("health"));
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to load shield reactor health", e);
        }
        return reactorHealth;
    }

    public Map<String, Long> loadShieldReactorDamageTimes() {
        Map<String, Long> lastDamageTime = new HashMap<>();
        String sql = "SELECT faction_name, last_damage_time FROM shield_reactor WHERE last_damage_time IS NOT NULL";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                lastDamageTime.put(rs.getString("faction_name"), rs.getLong("last_damage_time"));
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to load shield reactor damage times", e);
        }
        return lastDamageTime;
    }
}