package com.roki.core.Portals;

import com.roki.core.RoyalKingdomsCore;
import com.roki.core.commands.PortalCommandController;

import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerMoveEvent;

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
}