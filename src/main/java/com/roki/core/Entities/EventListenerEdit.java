package com.roki.core.Entities;
import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.server.DataPacketReceiveEvent;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import nukkitcoders.mobplugin.EventListener;
import nukkitcoders.mobplugin.entities.HorseBase;
import nukkitcoders.mobplugin.entities.animal.walking.Llama;
import nukkitcoders.mobplugin.entities.animal.walking.Pig;
import nukkitcoders.mobplugin.entities.animal.walking.Strider;

public class EventListenerEdit extends EventListener{

    @Override
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void DataPacketReceiveEvent(DataPacketReceiveEvent ev) {
        if (ev.getPacket() instanceof PlayerAuthInputPacket) {
            Player p = ev.getPlayer();
            if (!p.locallyInitialized) {
                return;
            }
            PlayerAuthInputPacket pk = (PlayerAuthInputPacket) ev.getPacket();
            double inputX = pk.getMotion().getX();
            double inputY = pk.getMotion().getY();
            System.out.println("Input X: " + inputX + " Input Y: " + inputY + p.riding.getName());
            if (inputX >= -1.0 && inputX <= 1.0 && inputY >= -1.0 && inputY <= 1.0) {
                if (p.riding instanceof HorseBase && !(p.riding instanceof Llama)) {
                    ((HorseBase) p.riding).onPlayerInput(p, inputX, inputY);
                } else if (p.riding instanceof Pig) {
                    ((Pig) p.riding).onPlayerInput(p, inputX, inputY);
                } else if (p.riding instanceof Strider) {
                    ((Strider) p.riding).onPlayerInput(p, inputX, inputY);
                } else if (p.riding instanceof PhoenixEntity) {
                    ((PhoenixEntity) p.riding).onPlayerInput(p, inputX, inputY);
                }
            }
        }
    }
}
