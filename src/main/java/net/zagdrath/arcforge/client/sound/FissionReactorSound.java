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
import net.zagdrath.arcforge.blockentity.multiblock.FissionReactorBlockEntity;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.registry.ModSounds;

// The Fission Reactor's coolant pumps: play from the middle of the vessel while the core runs (its controller lit),
// quiet near the source level and louder up to full power, fading in and out over about a second.
public class FissionReactorSound extends AbstractTickableSoundInstance {
    private static final Map<BlockPos, FissionReactorSound> PLAYING = new HashMap<>();
    private static final float FADE = 0.05F;

    private final Level level;
    private final BlockPos controller;
    private float fade;

    private FissionReactorSound(FissionReactorBlockEntity reactor, VesselStructure.Box box) {
        super(ModSounds.FISSION_REACTOR_RUN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.level = reactor.getLevel();
        this.controller = reactor.getBlockPos().immutable();
        this.x = (box.min().getX() + box.max().getX() + 1) * 0.5;
        this.y = box.min().getY() + 1.5;
        this.z = (box.min().getZ() + box.max().getZ() + 1) * 0.5;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
    }

    // Called every client tick by a formed controller (see FissionReactorBlockEntity.clientHook).
    public static void clientTick(FissionReactorBlockEntity reactor) {
        VesselStructure.Box box = reactor.getBox();
        if (box == null || !reactor.isRunning()) {
            return;
        }
        FissionReactorSound sound = PLAYING.get(reactor.getBlockPos());
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        if (sound != null && !sound.isStopped() && sounds.isActive(sound)) {
            return;
        }
        if (sound != null) {
            sound.stop();
        }
        sound = new FissionReactorSound(reactor, box);
        PLAYING.put(sound.controller, sound);
        sounds.play(sound);
    }

    @Override
    public void tick() {
        FissionReactorBlockEntity reactor = level == Minecraft.getInstance().level && level.getBlockEntity(controller) instanceof FissionReactorBlockEntity r
                ? r : null;
        boolean running = reactor != null && reactor.isRunning();
        fade = Math.clamp(fade + (running ? FADE : -FADE), 0.0F, 1.0F);
        // From 0.3 at the source level to 0.8 at full power, with the glow's brightness.
        volume = fade * (0.3F + 0.5F * (reactor != null ? reactor.glowLevel() : 0.0F));
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
