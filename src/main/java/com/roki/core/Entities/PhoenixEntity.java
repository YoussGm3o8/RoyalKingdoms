package com.roki.core.Entities;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.entity.custom.EntityManager;
import cn.nukkit.entity.mob.EntityEnderDragon;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

public class PhoenixEntity extends EntityEnderDragon implements CustomEntity {
    public static final String IDENTIFIER = "custom:phoenix";

    public static final EntityDefinition DEFINITION =
            EntityDefinition.builder().identifier(PhoenixEntity.IDENTIFIER).implementation(PhoenixEntity.class).build();

    public PhoenixEntity(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
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
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }
        super.onUpdate(currentTick);
        // Example behavior: Keep the entity in the air
        this.motionY = 0.0;
        this.updateMovement();
        return true;
    }
}
