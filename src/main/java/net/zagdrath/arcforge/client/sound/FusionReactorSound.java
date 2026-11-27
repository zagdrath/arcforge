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
import net.zagdrath.arcforge.blockentity.fusion.FusionReactorBlockEntity;
import net.zagdrath.arcforge.fusion.TokamakStructure;
import net.zagdrath.arcforge.registry.ModSounds;

// A Fusion Reactor's plasma: plays from the middle of the reactor while a plasma burns, quiet while it starts and louder
// as it brightens, fading in and out over about a second.
public class FusionReactorSound extends AbstractTickableSoundInstance {
    private static final Map<BlockPos, FusionReactorSound> PLAYING = new HashMap<>();
    private static final float FADE = 0.05F;

    private final Level level;
    private final BlockPos controller;
    private float fade;

    private FusionReactorSound(FusionReactorBlockEntity reactor, TokamakStructure.Shape shape) {
        super(ModSounds.FUSION_REACTOR_RUN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.level = reactor.getLevel();
        this.controller = reactor.getBlockPos().immutable();
        this.x = shape.axis().getX() + 0.5;
        this.y = shape.axis().getY() + 0.5;
        this.z = shape.axis().getZ() + 0.5;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
    }

    // Called every client tick by a formed controller (see FusionReactorBlockEntity.clientHook).
    public static void clientTick(FusionReactorBlockEntity reactor) {
        TokamakStructure.Shape shape = reactor.getShape();
        if (shape == null || !reactor.isAlive()) {
            return;
        }
        FusionReactorSound sound = PLAYING.get(reactor.getBlockPos());
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        if (sound != null && !sound.isStopped() && sounds.isActive(sound)) {
            return;
        }
        if (sound != null) {
            sound.stop();
        }
        sound = new FusionReactorSound(reactor, shape);
        PLAYING.put(sound.controller, sound);
        sounds.play(sound);
    }

    @Override
    public void tick() {
        FusionReactorBlockEntity reactor = level == Minecraft.getInstance().level && level.getBlockEntity(controller) instanceof FusionReactorBlockEntity r
                ? r : null;
        boolean running = reactor != null && reactor.isAlive();
        fade = Math.clamp(fade + (running ? FADE : -FADE), 0.0F, 1.0F);
        volume = fade * (0.35F + 0.04F * (reactor != null ? reactor.getGlow() : 0));
        pitch = 0.9F + 0.015F * (reactor != null ? reactor.getGlow() : 0);
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
