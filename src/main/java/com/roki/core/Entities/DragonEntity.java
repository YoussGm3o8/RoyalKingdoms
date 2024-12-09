package com.roki.core.Entities;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.entity.mob.EntityEnderDragon;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.ExplodeParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.SetEntityLinkPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;

public class DragonEntity extends EntityEnderDragon implements CustomEntity {
    private boolean saddled;
    private int health = 200;
        private int stayTime;
                private int moveTime;
                                private Object route;
                                                                private Object target;
                                                            
                                                                public static final String IDENTIFIER = "custom:dragon";
                                                            
                                                                public static final EntityDefinition DEFINITION =
                                                                        EntityDefinition.builder().identifier(DragonEntity.IDENTIFIER).implementation(DragonEntity.class).build();
                                                            
                                                                public DragonEntity(FullChunk chunk, CompoundTag nbt) {
                                                                    super(chunk, nbt);
                                                                    this.health = 200;
                                                                    this.setSaddled(true);
                                                                }
                                                            
                                                                @Override
                                                                public EntityDefinition getEntityDefinition() {
                                                                    return DEFINITION;
                                                                }
                                                            
                                                                @Override
                                                                public int getNetworkId() {
                                                                    return EntityEnderDragon.NETWORK_ID;
                                                                }
                                                            
                                                                @Override
                                                                public void initEntity() {
                                                                    super.initEntity();
                                                                    if (this.namedTag.contains("Saddle")) {
                                                                        this.setSaddled(this.namedTag.getBoolean("Saddle"));
                                                                    }
                                                                }
                                                            
                                                                @Override
                                                                public void saveNBT() {
                                                                    super.saveNBT();
                                                                    this.namedTag.putBoolean("Saddle", this.isSaddled());
                                                                }
                                                                
                                                                @Override
public boolean mountEntity(Entity entity, byte mode) {
    if (entity.riding != null) {
        dismountEntity(entity);
    }

    if (entity instanceof Player && !((Player) entity).isSleeping()) {
        broadcastLinkPacket(entity, SetEntityLinkPacket.TYPE_RIDE);
        entity.riding = this;
        entity.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, true);
        passengers.add(entity);
    }

    return true;
}
                                                                
                                                        
                                                            @Override
                                                            public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
                                                                if (this.passengers.isEmpty() && !player.isSneaking()) {
                                                                    if (player.riding == null) {
                                                                        this.mountEntity(player);
                                                                    }
                                                                }
                                                                return true;
                                                            }
                                                        
    @Override
    public boolean onUpdate(int currentTick) {
        for (Iterator<Entity> iterator = this.passengers.iterator(); iterator.hasNext();) {
            Entity linked = iterator.next();
            if (!linked.isAlive() || linked.riding != this) {
                iterator.remove();
                if (linked instanceof Player) {
                    ((Player) linked).setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, false);
                }
            }
        }
        return super.onUpdate(currentTick);
    }

public void onPlayerInput(Player player, double strafe, double forward) {
    this.stayTime = 0;
    this.moveTime = 10;
    this.route = null;
    this.target = null;

    if (forward < 0) {
        forward = forward / 2; // Reduce backward speed
    }

    strafe *= 0.4;  // Adjust strafe sensitivity

    double f = strafe * strafe + forward * forward;
    double friction = 0.6;

    this.yaw = player.yaw;

    if (f >= 1.0E-4) {
        f = Math.sqrt(f);

        if (f < 1) {
            f = 1;
        }

        f = friction / f;
        strafe = strafe * f;
        forward = forward * f;

        double f1 = Math.sin(this.yaw * 0.017453292);
        double f2 = Math.cos(this.yaw * 0.017453292);

        // Calculate motionX and motionZ
        this.motionX = (strafe * f2 - forward * f1);
        this.motionZ = (forward * f2 + strafe * f1);
    } else {
        this.motionX = 0;
        this.motionZ = 0;
    }

    // Handle vertical movement (up/down)
    if (this.passengers.get(0) instanceof Player) {
        Player rider = (Player) this.passengers.get(0);

        if (rider.getInventory().getItemInHand().getId() == 288) { // Feather for going up
            this.motionY = 0.5;
        } else if (rider.isSneaking()) { // Sneaking for going down
            this.motionY = -0.5;
            shootFireball();  // Fireball when sneaking
        } else {
            this.motionY = 0;
        }
    }
}

    @Override
    public boolean dismountEntity(Entity entity) {
        if (entity instanceof Player) {
            entity.riding = null;
            entity.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, false);
            passengers.remove(entity);
        }
        return true;
    }

    @Override
    public void updatePassengers() {
        if (this.passengers.isEmpty()) {
            return;
        }

        for (Entity passenger : new ArrayList<>(this.passengers)) {
            if (!passenger.isAlive()) {
                dismountEntity(passenger);
                passenger.resetFallDistance();
                continue;
            }

            updatePassengerPosition(passenger);
        }
    }

    private void shootFireball() {
        if (this.passengers.isEmpty() || !(this.passengers.get(0) instanceof Player)) {
            return;
        }
    
        Player rider = (Player) this.passengers.get(0);
        Vector3 direction = rider.getDirectionVector();
        
        // Create and configure the projectile
        Entity projectile = Entity.createEntity("Fireball", this.chunk, Entity.getDefaultNBT(this.add(0, 1.5, 0).add(direction.multiply(2))));
        if (projectile != null) {
            projectile.setMotion(direction.multiply(1.5));
            projectile.spawnToAll();
    
            // Add particle effect at the shoot location
            this.level.addParticle(new ExplodeParticle(this.add(0, 1.5, 0).add(direction.multiply(2))));
        }
    }

    public void attack(double damage) {
        this.health -= damage;
        if (this.health <= 0) {
            this.close();
        }
    }

    public boolean isSaddled() {
        return true; // Always saddled
    }

    public void setSaddled(boolean saddled) {
        this.saddled = true; // Always keep dragon saddled
    }
    
    public boolean canDespawn() {
        return false; // Prevent despawning when saddled
    }

    @Override
    public void close() {
        for (Entity passenger : new ArrayList<>(this.passengers)) {
            dismountEntity(passenger);
        }
        super.close();
    }
}