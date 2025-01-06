package com.roki.core.Entities;

import nukkitcoders.mobplugin.entities.FlyingEntity;
import nukkitcoders.mobplugin.entities.BaseEntity;
import com.roki.core.Entities.DragonBase;


import cn.nukkit.Player;
import cn.nukkit.entity.*;
import cn.nukkit.entity.data.FloatEntityData;
import cn.nukkit.entity.data.LongEntityData;
import cn.nukkit.entity.data.Vector3fEntityData;
import cn.nukkit.entity.mob.EntityEnderDragon;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.SetEntityLinkPacket;
import nukkitcoders.mobplugin.utils.FastMathLite;
import nukkitcoders.mobplugin.utils.Utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;

public class DragonBase extends FlyingEntity implements EntityRideable {

    private boolean saddled;

    public DragonBase(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
        this.noFallDamage = true;
        this.setSaddled(true);
        this.setMaxHealth(200); // Set maximum health
        this.setHealth(200);    // Explicitly set current health
        this.heal(200); 
    }

    @Override
    public int getNetworkId() {
        return EntityEnderDragon.NETWORK_ID; // Unique ID for the Dragon entity
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : Utils.rand(1, 3); // Experience on kill
    }

    @Override
    protected void initEntity() {
        super.initEntity();
        // Set base attributes
        this.setMaxHealth(200);
        this.setHealth(200);
        
        // Set data properties for health using correct constants
        this.setDataProperty(new FloatEntityData(DATA_HEALTH, 200f));
        
        this.setSaddled(true);
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        // Prevent instant death scenarios
        if (source.getDamage() >= this.getHealth()) {
            source.setDamage(this.getHealth() - 1);
        }

        // Limit maximum damage per hit
        if (source.getDamage() > 30) {
            source.setDamage(30);
        }

        boolean result = super.attack(source);
        
        // Ensure health doesn't go below 1
        if (this.getHealth() < 1) {
            this.setHealth(1);
            this.setDataProperty(new FloatEntityData(DATA_HEALTH, 1f));
        }
        
        return result;
    }


    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putBoolean("Saddle", this.isSaddled());
    }

    @Override
    public boolean mountEntity(Entity entity, byte mode) {
        Objects.requireNonNull(entity, "The target of the mounting entity can't be null");

        if (entity.riding != null) {
            dismountEntity(entity);
            entity.resetFallDistance();
            this.motionX = 0;
            this.motionZ = 0;
            this.stayTime = 20;
        } else {
            if (entity instanceof Player && ((Player) entity).isSleeping()) {
                return false;
            }

            if (isPassenger(entity)) {
                return false;
            }

            broadcastLinkPacket(entity, SetEntityLinkPacket.TYPE_RIDE);
            entity.riding = this;
            entity.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, true);
            entity.setDataProperty(new Vector3fEntityData(DATA_RIDER_SEAT_POSITION, new Vector3f(0, 2.5f, 0))); // Adjust seat position for dragon
            passengers.add(entity);
        }

        return true;
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (this.isFeedItem(item) && !this.isInLoveCooldown()) {
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_EAT);
            this.level.addParticle(new ItemBreakParticle(this.add(0, this.getMountedYOffset(), 0), Item.get(item.getId(), 0, 1)));
            return true;
        } else if (this.canBeSaddled() && !this.isSaddled() && item.getId() == Item.SADDLE) {
            player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_SADDLE);
            this.setSaddled(true);
        } else if (this.passengers.isEmpty() && !this.isBaby() && !player.isSneaking() && (!this.canBeSaddled()) ) {
            if (player.riding == null) {
                this.mountEntity(player);
            }
        }

        return super.onInteract(player, item, clickedPos);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        Iterator<Entity> linkedIterator = this.passengers.iterator();
        while (linkedIterator.hasNext()) {
            Entity linked = linkedIterator.next();
            if (!linked.isAlive()) {
                if (linked.riding == this) {
                    linked.riding = null;
                }
                linkedIterator.remove();
            }
        }
        return super.onUpdate(currentTick);
    }

    @Override
    public boolean canDespawn() {
        if (this.isSaddled()) {
            return false;
        }
        return super.canDespawn();
    }

    @Override
    public void updatePassengers() {
        if (this.passengers.isEmpty()) {
            return;
        }

        for (Entity passenger : new ArrayList<>(this.passengers)) {
            if (!passenger.isAlive() || Utils.entityInsideWaterFast(this)) {
                dismountEntity(passenger);
                passenger.resetFallDistance();
                continue;
            }
            updatePassengerPosition(passenger);
        }
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        return this.passengers.isEmpty();
    }

    public boolean canBeSaddled() {
        return !this.isBaby();
    }

    public boolean isSaddled() {
        return saddled;
    }

    public void setSaddled(boolean saddled) {
        this.saddled = saddled;
    }

    // Override methods for flying behavior
    @Override
    protected void checkTarget() {
        if (this.isKnockback()) {
            return;
        }

        Player closestPlayer = null;
        double closestDistance = Double.MAX_VALUE;

        for (Entity entity : this.getLevel().getEntities()) {
            if (entity instanceof Player && !entity.closed && entity.isAlive()) {
                double distance = this.distanceSquared(entity);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestPlayer = (Player) entity;
                }
            }
        }

        if (closestPlayer != null && closestDistance > 100) { // 10 blocks squared
            this.target = closestPlayer;
        } else {
            this.target = null;
        }
    }

    public boolean isFeedItem(Item item) {
        return item.getId() == Item.BEEF ||
                item.getId() == Item.CHICKEN ||
                item.getId() == Item.GOLDEN_APPLE;
    }

    @Override
    public Vector3 updateMove(int tickDiff) {
        if (!this.isInTickingRange()) {
            return null;
        }

        if (this.isMovement() && !isImmobile()) {
            if (this.isKnockback()) {
                this.move(this.motionX, this.motionY, this.motionZ);
                this.updateMovement();
                return null;
            }

            if (this.followTarget != null && !this.followTarget.closed && this.followTarget.isAlive()) {
                double x = this.followTarget.x - this.x;
                double y = this.followTarget.y - this.y;
                double z = this.followTarget.z - this.z;

                double diff = Math.abs(x) + Math.abs(z);
                if (diff == 0 || this.stayTime > 0 || this.distance(this.followTarget) <= (this.getWidth() / 2 + 0.05)) {
                    this.motionX = 0;
                    this.motionY = this.getSpeed() * 0.01 * y;
                    this.motionZ = 0;
                } else {
                    this.motionX = this.getSpeed() * 0.15 * (x / diff);
                    this.motionY = this.getSpeed() * 0.27 * (y / diff);
                    this.motionZ = this.getSpeed() * 0.15 * (z / diff);
                }
                if ((this.stayTime <= 0 || Utils.rand()) && diff != 0) {
                    this.yaw = (FastMathLite.toDegrees(-FastMathLite.atan2(x / diff, z / diff))) + 90;
                    this.move(this.motionX, this.motionY, this.motionZ);
                }
            }
        }

        return super.updateMove(tickDiff);
    }
}
