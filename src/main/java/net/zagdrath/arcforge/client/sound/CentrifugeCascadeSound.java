/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.sound;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.blockentity.multiblock.CentrifugeCascadeBlockEntity;
import net.zagdrath.arcforge.multiblock.CentrifugeCascadeStructure;
import net.zagdrath.arcforge.registry.ModSounds;

// The Gas Centrifuge Cascade's faint hum: plays from the middle of the hall while it enriches (its controller lit),
// a little louder the more centrifuges it has, fading in and out over about a second.
public class CentrifugeCascadeSound extends AbstractTickableSoundInstance {
    private static final Map<BlockPos, CentrifugeCascadeSound> PLAYING = new HashMap<>();
    private static final float FADE = 0.05F;

    private final Level level;
    private final BlockPos controller;
    private float fade;

    private CentrifugeCascadeSound(CentrifugeCascadeBlockEntity cascade, CentrifugeCascadeStructure.Box box) {
        super(ModSounds.CENTRIFUGE_CASCADE_RUN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.level = cascade.getLevel();
        this.controller = cascade.getBlockPos().immutable();
        this.x = (box.min().getX() + box.max().getX() + 1) * 0.5;
        this.y = box.min().getY() + 1.5;
        this.z = (box.min().getZ() + box.max().getZ() + 1) * 0.5;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
    }

    // Called every client tick by a formed controller (see CentrifugeCascadeBlockEntity.clientHook).
    public static void clientTick(CentrifugeCascadeBlockEntity cascade) {
        CentrifugeCascadeStructure.Box box = cascade.getBox();
        if (box == null || !cascade.isRunning()) {
            return;
        }
        CentrifugeCascadeSound sound = PLAYING.get(cascade.getBlockPos());
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        if (sound != null && !sound.isStopped() && sounds.isActive(sound)) {
            return;
        }
        if (sound != null) {
            sound.stop();
        }
        sound = new CentrifugeCascadeSound(cascade, box);
        PLAYING.put(sound.controller, sound);
        sounds.play(sound);
    }

    @Override
    public void tick() {
        boolean running = level == Minecraft.getInstance().level && level.getBlockEntity(controller) instanceof CentrifugeCascadeBlockEntity cascade
                && cascade.isRunning();
        int centrifuges = level.getBlockEntity(controller) instanceof CentrifugeCascadeBlockEntity cascade ? cascade.getCentrifuges() : 0;
        fade = Math.clamp(fade + (running ? FADE : -FADE), 0.0F, 1.0F);
        // A faint hum: from 0.25 with a few centrifuges to 0.55 with a full hall.
        volume = fade * (0.25F + 0.3F * Math.min(1.0F, centrifuges / 40.0F));
        if (!running && fade <= 0.0F) {
            PLAYING.remove(controller, this);
            stop();
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
