package com.roki.core.Portals;

import com.roki.core.RoyalKingdomsCore;
import com.roki.core.commands.PortalCommandController;

import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.level.Position;

import java.util.Map;

public class PortalEventListener implements Listener {
    private RoyalKingdomsCore plugin;

    public PortalEventListener(RoyalKingdomsCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        plugin.portalCommandController.handlePortalStickInteraction(event);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        plugin.portalCommandController.checkPortalEntry(event);
    }

    public void onEnable() {
        reschedulePortalParticles();
    }

    private void reschedulePortalParticles() {
        Map<String, Object> portals = plugin.portalCommandController.portalConfig.getAll();
        for (String portalName : portals.keySet()) {
            Map<String, Object> portalData = (Map<String, Object>) portals.get(portalName);
            String world = (String) portalData.get("world");
            double x1 = ((Number) portalData.get("x1")).doubleValue();
            double y1 = ((Number) portalData.get("y1")).doubleValue();
            double z1 = ((Number) portalData.get("z1")).doubleValue();
            double x2 = ((Number) portalData.get("x2")).doubleValue();
            double y2 = ((Number) portalData.get("y2")).doubleValue();
            double z2 = ((Number) portalData.get("z2")).doubleValue();
            String color = (String) portalData.get("color");

            Position p1 = new Position(x1, y1, z1, plugin.getServer().getLevelByName(world));
            Position p2 = new Position(x2, y2, z2, plugin.getServer().getLevelByName(world));

            plugin.portalCommandController.displayPortalParticles(p1, p2, color);
        }
    }
}