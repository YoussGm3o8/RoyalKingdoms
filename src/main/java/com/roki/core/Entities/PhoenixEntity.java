package com.roki.core.Entities;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityRideable;
import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.entity.data.FloatEntityData;
import cn.nukkit.entity.data.Vector3fEntityData;
import cn.nukkit.entity.mob.EntityEnderDragon;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.SetEntityLinkPacket;
import nukkitcoders.mobplugin.MobPlugin;
import nukkitcoders.mobplugin.entities.HorseBase;
import nukkitcoders.mobplugin.entities.animal.FlyingAnimal;
import nukkitcoders.mobplugin.entities.animal.walking.Donkey;
import nukkitcoders.mobplugin.entities.animal.walking.Horse;
import nukkitcoders.mobplugin.utils.FastMathLite;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PhoenixEntity extends HorseBase implements CustomEntity {
    public static final String IDENTIFIER = "custom:phoenix";
    public static final EntityDefinition DEFINITION =
            EntityDefinition.builder().identifier(PhoenixEntity.IDENTIFIER).implementation(PhoenixEntity.class).build();

    private static final float DEFAULT_MOVE_SPEED = 2.0f;
    private static final float VERTICAL_MOTION_UP = 0.4f;
    private static final float VERTICAL_MOTION_DOWN = -0.4f;
    private static final float GRAVITY = -0.1f;
    private static final float PASSENGER_HEIGHT_OFFSET = 2.5f;

    private boolean isTeleporting = false; // Flag to track teleportation

    protected ArrayList<Entity> passengers = new ArrayList<>();
    protected float moveSpeed = DEFAULT_MOVE_SPEED;

    public PhoenixEntity(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
        this.setMaxHealth(200);
        this.setHealth(200);
        this.setDataProperty(new FloatEntityData(DATA_HEALTH, 200f));
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(200);
        super.initEntity();
        this.setHealth(200);

        this.fireProof = true;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_FIRE_IMMUNE, true);
        this.setDataProperty(new FloatEntityData(DATA_HEALTH, 200f));

        // Ensure the entity has a saddle by default
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_SADDLED, true);

        this.setDataProperty(new FloatEntityData(DATA_BOUNDING_BOX_WIDTH, this.getWidth()));
        this.setDataProperty(new FloatEntityData(DATA_BOUNDING_BOX_HEIGHT, this.getHeight()));
        
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

        pk.attributes = new cn.nukkit.entity.Attribute[]{
                cn.nukkit.entity.Attribute.getAttribute(cn.nukkit.entity.Attribute.MAX_HEALTH).setMaxValue(200.0f).setValue(200.0f),
                cn.nukkit.entity.Attribute.getAttribute(cn.nukkit.entity.Attribute.MOVEMENT_SPEED).setValue(moveSpeed)
        };

        pk.metadata = this.dataProperties;
        player.dataPacket(pk);

        super.spawnTo(player);
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
            entity.setDataProperty(new Vector3fEntityData(DATA_RIDER_SEAT_POSITION, new Vector3f(-0.5f, 4f, -1f)));
            passengers.add(entity);
        }

        return true;
    }
    
    @Override
    public boolean dismountEntity(Entity entity) {
        if (entity.riding == null || !this.passengers.contains(entity)) {
            return false;
        }

        if (isTeleporting) {
            // Avoid further teleportation if already in progress
            return false;
        }

        isTeleporting = true; // Mark teleportation as in progress

        SetEntityLinkPacket pk = new SetEntityLinkPacket();
        pk.vehicleUniqueId = this.getId();
        pk.riderUniqueId = entity.getId();
        pk.type = SetEntityLinkPacket.TYPE_REMOVE;
        Server.broadcastPacket(this.getViewers().values(), pk);

        entity.riding = null;
        entity.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, false);
        this.passengers.remove(entity);

        // Perform teleportation if necessary
        if (entity instanceof Player) {
            Player player = (Player) entity;
            Vector3 currentPosition = this.getPosition();  // Get the current position of the entity
            player.teleport(currentPosition);  // Teleport the player to the Phoenix position
        }

        isTeleporting = false; // Reset the teleportation flag
        return true;
    }
    
    // public boolean onUpdate(int currentTick) {
    //     Iterator<Entity> linkedIterator = this.passengers.iterator();
  
    //     while(linkedIterator.hasNext()) {
    //        Entity linked = (Entity)linkedIterator.next();
    //        if (!linked.isAlive()) {
    //           if (linked.riding == this) {
    //              linked.riding = null;
    //           }
  
    //           linkedIterator.remove();
    //        }
    //     }
    //     this.move(0, this.motionY, 0);
    //     return super.onUpdate(currentTick);
    //  }

    @Override
    public void onPlayerInput(Player player, double strafe, double forward) {
        this.stayTime = 0;
        this.moveTime = 10;
        this.route = null;
        this.target = null;
    
        double playerYaw = (player.getYaw() + 180) % 360; // Get the player's yaw and normalize it
        double playerPitch = player.getPitch();

        this.setRotation(playerYaw, playerPitch);

        if (forward < 0) {
            forward = forward / 2;
        }
        forward = -forward;
        strafe = -strafe;
    
        strafe *= 0.4;
    
        double f = strafe * strafe + forward * forward;
        double friction = 0.91; // Friction for smoother movement
    
        // Make the dragon face the direction the player is looking at

        
        
        // this.setYaw(playerYaw ); // Align the entity's yaw with the player's
        // this.setHeadYaw(playerYaw); // Align the entity's head yaw with the player's
        // this.setRotation(playerYaw, this.getPitch());

        this.yaw = playerYaw;
    
        // If the player is looking up (positive pitch), move the dragon upward
        if (playerPitch < -20) {  // You can adjust the threshold to match your needs
            this.pitch = playerPitch;
            this.motionY = VERTICAL_MOTION_UP;
        } 
        // If the player is looking down (negative pitch), move the dragon downward
        else if (playerPitch > 20) {  // You can adjust the threshold to match your needs
            this.pitch = playerPitch;
            this.motionY = VERTICAL_MOTION_DOWN;
        } 
        // If the player is looking straight, the dragon doesn't move vertically
        else {
            this.motionY = 0;
            this.pitch = playerPitch;
        }
    
        if (f >= 1.0E-4) {
            f = Math.sqrt(f);
    
            // if (f < 1) {
            //     f = 1;
            // }
    
            f = friction / f;
            strafe *= f;
            forward *= f;
    
            double yawRadians = Math.toRadians(-playerYaw); // Negative yaw for correct direction
            double sinYaw = Math.sin(yawRadians);             
            double cosYaw = Math.cos(yawRadians);
        
            this.motionX = (strafe * cosYaw + forward * sinYaw) * this.moveSpeed;
            this.motionZ = (forward * cosYaw - strafe * sinYaw) * this.moveSpeed;
        } else {
            this.motionX = 0;
            this.motionZ = 0;
        }
        System.out.println("Motion X: " + this.motionX + " Motion Y: " + this.motionY + " Motion Z:"  + this.motionZ);
    
        // Apply motion to the entity
        // this.move(this.motionX, this.motionY, this.motionZ);
        
        // // Force update rotation values
        // this.setRotation(playerYaw, playerPitch);
        
        // // Update movement
        // this.updateMovement();
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        if (source.getDamage() >= this.getHealth()) {
            source.setDamage(this.getHealth() - 1);
        }
        return super.attack(source);
    }

     @Override
    protected DataPacket createAddEntityPacket() {
        AddEntityPacket addEntity = new AddEntityPacket();
        addEntity.type = this.getNetworkId();
        addEntity.entityUniqueId = this.getId();
        addEntity.entityRuntimeId = this.getId();
        addEntity.yaw = (float) this.yaw;
        addEntity.headYaw = (float) this.yaw;
        addEntity.pitch = (float) this.pitch;
        addEntity.x = (float) this.x;
        addEntity.y = (float) this.y;
        addEntity.z = (float) this.z;
        addEntity.speedX = (float) this.motionX;
        addEntity.speedY = (float) this.motionY;
        addEntity.speedZ = (float) this.motionZ;
        addEntity.metadata = this.dataProperties;
        addEntity.attributes = new Attribute[]{Attribute.getAttribute(Attribute.MAX_HEALTH).setMaxValue(200).setValue(200)};
        return addEntity;
    }

    @Override
    public float getWidth() {
        return 3.0f; // Increased from default
    }

    @Override
    public float getHeight() {
        return 3.0f; // Increased from default
    }

    @Override
    public float getLength() {
        return 4.0f; // Added length parameter
    }
   
    @Override
    public String getName() {
        return this.hasCustomName() ? this.getNameTag() : "Phoenix";
    }

    @Override
    public void updatePassengers() {
        if (this.passengers.isEmpty()) {
            return;
        }

        for (Entity passenger : new ArrayList<>(this.passengers)) {
            if (!passenger.isAlive() || passenger.riding != this) {
                dismountEntity(passenger);
                continue;
            }

            passenger.setPosition(new Vector3(
                    this.x,
                    this.y + PASSENGER_HEIGHT_OFFSET,
                    this.z
            ));
        }
    }

    @Override
    public int getKillExperience() {
        if (!MobPlugin.getInstance().config.noXpOrbs) {
            for (int i = 0; i < 167; ) {
                this.level.dropExpOrb(this, 3);
                i++;
            }
        }
        return 0;
    }
}
