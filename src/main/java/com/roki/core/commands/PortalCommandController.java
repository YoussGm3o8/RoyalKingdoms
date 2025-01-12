package com.roki.core.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.roki.core.RoyalKingdomsCore;
import com.roki.core.database.DatabaseManager;
import com.roki.core.database.DataModel;

import cn.nukkit.Player;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;
import cn.nukkit.utils.Config;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.level.particle.DustParticle;
import cn.nukkit.level.particle.SpellParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.scheduler.Task;

public class PortalCommandController {
    private final RoyalKingdomsCore plugin;
    private DatabaseManager dbManager;
    
    public Config portalConfig;
    private Map<String, Position> pos1 = new HashMap<>();
    private Map<String, Position> pos2 = new HashMap<>();

    public PortalCommandController(RoyalKingdomsCore plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        this.portalConfig = new Config(plugin.getDataFolder() + "/portals.yml", Config.YAML);
        loadPortalsFromDatabase();
    }

    private void loadPortalsFromDatabase() {
        String sql = "SELECT * FROM portals";
        try (Connection conn = dbManager.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String portalName = rs.getString("name");
                String world = rs.getString("world");
                double x1 = rs.getDouble("x1");
                double y1 = rs.getDouble("y1");
                double z1 = rs.getDouble("z1");
                double x2 = rs.getDouble("x2");
                double y2 = rs.getDouble("y2");
                double z2 = rs.getDouble("z2");
                String color = rs.getString("color");
                String command = rs.getString("command");

                Position p1 = new Position(x1, y1, z1, plugin.getServer().getLevelByName(world));
                Position p2 = new Position(x2, y2, z2, plugin.getServer().getLevelByName(world));

                Map<String, Object> portalData = new HashMap<>();
                portalData.put("world", world);
                portalData.put("x1", x1);
                portalData.put("y1", y1);
                portalData.put("z1", z1);
                portalData.put("x2", x2);
                portalData.put("y2", y2);
                portalData.put("z2", z2);
                portalData.put("color", color);
                portalData.put("command", command);

                portalConfig.set(portalName, portalData);
                displayPortalParticles(p1, p2, color);
            }
        } catch (SQLException e) {
            plugin.getLogger().error("Failed to load portals from database", e);
        }
    }

    public boolean handlePortalStickCommand(Player player) {
        Item portalStick = Item.get(Item.STICK, 0, 1);
        portalStick.setCustomName("§aPortal Creation Stick");
        player.getInventory().addItem(portalStick);
        player.sendMessage("§aReceived Portal Creation Stick");
        return true;
    }

    public boolean handleSetPortalCommand(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("§cUsage: /setportal <name> <color>");
            return true;
        }

        String portalName = args[0].toLowerCase();
        String color = args[1].toLowerCase();

        // Check if both positions are set
        if (!pos1.containsKey(player.getName()) || !pos2.containsKey(player.getName())) {
            player.sendMessage("§cYou must set both positions first using the portal stick!");
            return true;
        }

        // Get positions
        Position p1 = pos1.get(player.getName());
        Position p2 = pos2.get(player.getName());

        // Save portal data
        Map<String, Object> portalData = new HashMap<>();
        portalData.put("world", p1.getLevel().getName());
        portalData.put("x1", p1.getX());
        portalData.put("y1", p1.getY());
        portalData.put("z1", p1.getZ());
        portalData.put("x2", p2.getX());
        portalData.put("y2", p2.getY());
        portalData.put("z2", p2.getZ());
        portalData.put("color", color);
        portalData.put("command", ""); // Default empty command

        portalConfig.set(portalName, portalData);
        portalConfig.save();

        // Save to database
        savePortalToDatabase(portalName, p1, p2, color, "");

        // Display particle effects
        displayPortalParticles(p1, p2, color);

        // Clear positions
        pos1.remove(player.getName());
        pos2.remove(player.getName());

        player.sendMessage("§aPortal '" + portalName + "' created with color " + color + "!");
        return true;
    }

    private void savePortalToDatabase(String portalName, Position p1, Position p2, String color, String command) {
        String sql = "INSERT OR REPLACE INTO portals (name, world, x1, y1, z1, x2, y2, z2, color, command) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        dbManager.executeUpdate(sql, portalName, p1.getLevel().getName(), p1.getX(), p1.getY(), p1.getZ(), p2.getX(), p2.getY(), p2.getZ(), color, command);
    }

    public void displayPortalParticles(Position p1, Position p2, String color) {
        final int r, g, b; // Declare as final
        switch (color) {
            case "red":
                r = 255; g = 0; b = 0;
                break;
            case "green":
                r = 0; g = 255; b = 0;
                break;
            case "blue":
                r = 0; g = 0; b = 255;
                break;
            case "yellow":
                r = 255; g = 255; b = 0;
                break;
            case "purple":
                r = 128; g = 0; b = 128;
                break;
            case "cyan":
                r = 0; g = 255; b = 255;
                break;
            case "orange":
                r = 255; g = 165; b = 0;
                break;
            case "pink":
                r = 255; g = 192; b = 203;
                break;
            default:
                r = 255; g = 255; b = 255; // Default to white
                break;
        }

        double minX = Math.min(p1.getX(), p2.getX());
        double maxX = Math.max(p1.getX(), p2.getX());
        double maxY = Math.max(p1.getY(), p2.getY());
        double minZ = Math.min(p1.getZ(), p2.getZ());
        double maxZ = Math.max(p1.getZ(), p2.getZ());

        if (p1.getLevel() == null) {
            plugin.getLogger().error("Level is null for position: " + p1);
            return;
        }

        plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, new Task() {
            @Override
            public void onRun(int currentTick) {
                for (double x = minX; x <= maxX; x += 0.5) { // Increase step size to reduce density
                    for (double z = minZ; z <= maxZ; z += 0.5) { // Increase step size to reduce density
                        p1.getLevel().addParticle(new DustParticle(new Vector3(x, maxY, z), r, g, b));
                    }
                }
            }
        }, 20); // Increase interval to reduce frequency
    }

    public void checkPortalEntry(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Position playerPos = player.getLocation();
    
        for (String portalName : portalConfig.getKeys(false)) {
            Map<String, Object> portalData = (Map<String, Object>) portalConfig.get(portalName);
            
            // Verify portal is in the same world
            if (!playerPos.getLevel().getName().equals(portalData.get("world"))) continue;
    
            // Check portal bounds
            double x1 = ((Number) portalData.get("x1")).doubleValue();
            double y1 = ((Number) portalData.get("y1")).doubleValue();
            double z1 = ((Number) portalData.get("z1")).doubleValue();
            double x2 = ((Number) portalData.get("x2")).doubleValue();
            double y2 = ((Number) portalData.get("y2")).doubleValue();
            double z2 = ((Number) portalData.get("z2")).doubleValue();
    
            if (isWithinBounds(playerPos, x1, y1, z1, x2, y2, z2)) {
                String command = (String) portalData.getOrDefault("command", "");
                
                // Add debug logging
                
                if (!command.isEmpty()) {
                    try {
                        // Execute the command as the player
                        plugin.getServer().dispatchCommand(player, command.replace("%player%", player.getName()));
                    } catch (Exception e) {
                    }
                }
            }
        }
    }

    // Utility method to check if a position is within portal bounds
    private boolean isWithinBounds(Position pos, double x1, double y1, double z1, 
                                    double x2, double y2, double z2) {
        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);
        double minZ = Math.min(z1, z2);
        double maxZ = Math.max(z1, z2);

        return pos.getX() >= minX && pos.getX() <= maxX &&
               pos.getY() >= minY && pos.getY() <= maxY &&
               pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public void handlePortalStickInteraction(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Item item = event.getItem();

        if (item != null && item.getId() == Item.STICK && 
            item.getCustomName().equals("§aPortal Creation Stick")) {
            
            if (event.getAction() == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
                // Set first position
                pos1.put(player.getName(), event.getBlock().getLocation());
                player.sendMessage("§aPosition 1 set!");
                event.setCancelled(true);
            } else if (event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
                // Set second position
                pos2.put(player.getName(), event.getBlock().getLocation());
                player.sendMessage("§aPosition 2 set!");
                event.setCancelled(true);
            }
        }
    }

    public void createPortal(String portalName, String destination) {
        String sql = "INSERT INTO portals (name, destination) VALUES (?, ?)";
        dbManager.executeUpdate(sql, portalName, destination);
    }

    public void deletePortal(String portalName) {
        String sql = "DELETE FROM portals WHERE name = ?";
        dbManager.executeUpdate(sql, portalName);
    }
}
