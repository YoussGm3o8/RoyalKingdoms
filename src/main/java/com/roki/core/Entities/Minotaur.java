package com.roki.core.Entities;

import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.entity.custom.EntityManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import nukkitcoders.mobplugin.entities.monster.walking.Husk;

public class Minotaur extends Husk implements CustomEntity {
    public static final String IDENTIFIER = "custom:minotaur";
    public static final EntityDefinition DEFINITION =
            EntityDefinition.builder().identifier(Minotaur.IDENTIFIER).implementation(Minotaur.class).build();

    public Minotaur(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public EntityDefinition getEntityDefinition() {
        return DEFINITION;
    }

    @Override
    public int getNetworkId() {
        // Network IDs for custom entities are generated automatically
        return EntityManager.get().getRuntimeId(IDENTIFIER);
    }
}