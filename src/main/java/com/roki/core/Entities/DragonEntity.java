package com.roki.core.Entities;

import cn.nukkit.Player;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.entity.custom.EntityManager;
import cn.nukkit.entity.data.FloatEntityData;
import cn.nukkit.entity.data.LongEntityData;
import cn.nukkit.entity.mob.EntityEnderDragon;
import cn.nukkit.entity.passive.EntityHorse;
import cn.nukkit.event.entity.EntityDamageEvent;
import nukkitcoders.mobplugin.entities.monster.flying.EnderDragon;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.SetEntityLinkPacket;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.math.Vector3;
import java.util.ArrayList;
import java.util.Iterator;



public class DragonEntity extends DragonBase implements CustomEntity {
    public static final String IDENTIFIER = "custom:dragon";

    public static final EntityDefinition DEFINITION =
    EntityDefinition.builder().identifier(DragonEntity.IDENTIFIER).implementation(DragonEntity.class).build();
    
    private static final int DATA_MAX_HEALTH = 200;

    private Object route;

    public DragonEntity(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
        this.setSaddled(true);  // Ensure the dragon is saddled by default
        this.setMaxHealth(200); // Set a reasonable maximum health
        this.setHealth(this.getMaxHealth()); // Ensure the dragon starts with full health
    }
    
    @Override
    public void spawnTo(Player player) {
        AddEntityPacket pk = new AddEntityPacket();
        pk.type = this.getNetworkId();
        pk.entityUniqueId = this.getId();
        pk.entityRuntimeId = this.getId();
        pk.x = (float) this.x;
        pk.y = (float) this.y;
        pk.z = (float) this.z;
        pk.speedX = (float) this.motionX;
        pk.speedY = (float) this.motionY;
        pk.speedZ = (float) this.motionZ;
        pk.yaw = (float) this.yaw;
        pk.pitch = (float) this.pitch;

        // Create attributes array for the packet
        Attribute[] attributes = new Attribute[2];
        attributes[0] = Attribute.getAttribute(Attribute.MAX_HEALTH)
            .setMaxValue(200.0f)
            .setValue(200.0f);
        attributes[1] = Attribute.getAttribute(Attribute.MOVEMENT_SPEED)
            .setValue(1.0f);
        pk.attributes = attributes;
        
        pk.metadata = this.dataProperties;
        player.dataPacket(pk);
        
        super.spawnTo(player);
    }

        
    @Override
    public boolean entityBaseTick(int tickDiff) {
        boolean hasUpdate = super.entityBaseTick(tickDiff);
        
        // Periodically verify and restore health if needed
        if (this.getHealth() < 1) {
            this.setHealth(1);
            // Update health data property
            this.setDataProperty(new FloatEntityData(DATA_HEALTH, 1f));
        }
        
        return hasUpdate;
    }

    @Override
    public EntityDefinition getEntityDefinition() {
        return DEFINITION;
    }

    @Override
    public int getNetworkId() {
        return EntityHorse.NETWORK_ID;
    }

    @Override
    public void initEntity() {
        super.initEntity();
        if (this.namedTag.contains("Saddle")) {
            this.setSaddled(this.namedTag.getBoolean("Saddle"));
        }
    }

    @Override
    public boolean isAlive() {
        return true; // Ensure the entity always appears alive
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putBoolean("Saddle", this.isSaddled());
    }

    @Override
    public boolean attack(EntityDamageEvent event) {
        // Prevent instant death
        if (event.getFinalDamage() >= this.getHealth()) {
            event.setDamage(this.getHealth() - 1); // Leave at least 1 health
        }
        super.attack(event);
        return false; 
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
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_IMITATE_ENDER_DRAGON);
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

    @Override
    public boolean dismountEntity(Entity entity) {
        if (entity instanceof Player) {
            entity.riding = null;
            entity.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, false);
            passengers.remove(entity);
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_IMITATE_ENDER_DRAGON);
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

    public void onPlayerInput(Player player, double strafe, double forward) {
        this.stayTime = 0;
        this.moveTime = 10;
        this.route = null;
        this.target = null;
    
        // Force forward movement
        forward = 1; // Always move forward
    
        this.yaw = player.yaw;
    
        double f1 = Math.sin(this.yaw * 0.017453292);
        double f2 = Math.cos(this.yaw * 0.017453292);
    
        // Calculate motionX and motionZ for forward movement
        this.motionX = -forward * f1;
        this.motionZ = forward * f2;
    
        // Handle vertical movement
        if (this.passengers.get(0) instanceof Player) {
            Player rider = (Player) this.passengers.get(0);
    
            if (rider.getInventory().getItemInHand().getId() == 288) { // Feather for ascending
                this.motionY = 0.5;
            } else if (rider.isSneaking()) { // Sneaking for descending
                this.motionY = -0.5;
            } else {
                this.motionY = 0;
            }
        }
    }

    // private void shootFireball() {
    //     if (this.passengers.isEmpty() || !(this.passengers.get(0) instanceof Player)) {
    //         return;
    //     }

    //     Player rider = (Player) this.passengers.get(0);
    //     Vector3 direction = rider.getDirectionVector();

    //     // Create and configure the projectile
    //     Entity projectile = Entity.createEntity("Fireball", this.chunk, Entity.getDefaultNBT(this.add(0, 1.5, 0).add(direction.multiply(2))));

    //     if (projectile != null) {
    //         projectile.setMotion(direction.multiply(1.5));
    //         projectile.spawnToAll();
    //     }
    // }

    @Override
    public boolean canDespawn() {
        return true; // Prevent despawning when saddled
    }

    @Override
    public void close() {
        for (Entity passenger : new ArrayList<>(this.passengers)) {
            dismountEntity(passenger);
        }
        this.kill(); // Set the entity as dead
        this.despawnFromAll();

        super.close();
    }
}
