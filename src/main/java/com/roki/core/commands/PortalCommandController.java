package com.roki.core.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

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

public class PortalCommandController {
    private final RoyalKingdomsCore plugin;
    private DatabaseManager dbManager;
    
    public Config portalConfig;
    private Map<String, Position> pos1 = new HashMap<>();
    private Map<String, Position> pos2 = new HashMap<>();

    public PortalCommandController(RoyalKingdomsCore plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
    }

    public boolean handlePortalStickCommand(Player player) {
        Item portalStick = Item.get(Item.STICK, 0, 1);
        portalStick.setCustomName("§aPortal Creation Stick");
        player.getInventory().addItem(portalStick);
        player.sendMessage("§aReceived Portal Creation Stick");
        return true;
    }

    public boolean handleSetPortalCommand(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage("§cUsage: /setportal <name>");
            return true;
        }

        String portalName = args[0].toLowerCase();

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
        portalData.put("command", ""); // Default empty command

        portalConfig.set(portalName, portalData);
        portalConfig.save();

        // Clear positions
        pos1.remove(player.getName());
        pos2.remove(player.getName());

        player.sendMessage("§aPortal '" + portalName + "' created!");
        return true;
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
