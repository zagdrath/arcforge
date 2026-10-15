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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.blockentity.multiblock.DieselGeneratorBlockEntity;
import net.zagdrath.arcforge.registry.ModSounds;

// The Diesel Generator's running sound: a loop from the middle of the engine while it turns faster than 2% of full
// speed, its volume (0.3-1.0) and pitch (0.6-1.25, the firing beat climbing from a slow idle chug) following the engine
// and easing as it runs up and coasts. Also puffs exhaust smoke out of the silencer's outlet while it runs: a few dark
// puffs a second at idle, a steady plume at full speed, and heavier black smoke while it labours below 90% speed.
public class DieselGeneratorSound extends AbstractTickableSoundInstance {
    private static final Map<BlockPos, DieselGeneratorSound> PLAYING = new HashMap<>();
    private static final float SILENT_BELOW = 0.02F;
    private static final float EASE = 0.15F;
    // Smoke puffs a tick at full speed, and how fast they leave the outlet.
    private static final float SMOKE_PER_TICK = 1.5F;
    private static final double SMOKE_RISE = 0.12;

    private final DieselGeneratorBlockEntity generator;

    // Called every client tick by a formed Diesel Generator (see DieselGeneratorBlockEntity.clientTick).
    public static void clientTick(DieselGeneratorBlockEntity generator) {
        float speed = generator.getShownSpeed();
        spawnSmoke(generator, speed);
        if (speed < SILENT_BELOW) {
            return;
        }
        BlockPos pos = generator.getBlockPos();
        DieselGeneratorSound sound = PLAYING.get(pos);
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        // A sound the engine refused or dropped never ticks again, so it's replaced (see TurbineArraySound).
        if (sound != null && sound.generator == generator && !sound.isStopped() && sounds.isActive(sound)) {
            return;
        }
        if (sound != null) {
            sound.stop();
        }
        sound = new DieselGeneratorSound(generator);
        PLAYING.put(pos.immutable(), sound);
        sounds.play(sound);
    }

    private static void spawnSmoke(DieselGeneratorBlockEntity generator, float speed) {
        Level level = generator.getLevel();
        Vec3 outlet = generator.exhaustOutlet();
        if (level == null || outlet == null || speed < SILENT_BELOW) {
            return;
        }
        RandomSource random = level.getRandom();
        float count = SMOKE_PER_TICK * (0.15F + 0.85F * speed);
        boolean runningUp = speed < 0.9F;
        while (count > 0) {
            if (count < 1 && random.nextFloat() >= count) {
                break;
            }
            count--;
            double x = outlet.x + (random.nextDouble() - 0.5) * 0.12, z = outlet.z + (random.nextDouble() - 0.5) * 0.12;
            double rise = SMOKE_RISE * (0.6 + 0.6 * speed) * (0.8 + random.nextDouble() * 0.4);
            double driftX = (random.nextDouble() - 0.5) * 0.02, driftZ = (random.nextDouble() - 0.5) * 0.02;
            level.addParticle(runningUp || random.nextFloat() < 0.3F ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, x, outlet.y + 0.05, z, driftX,
                    rise, driftZ);
        }
    }

    private DieselGeneratorSound(DieselGeneratorBlockEntity generator) {
        super(ModSounds.DIESEL_GENERATOR_RUN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.generator = generator;
        Vec3 centre = generator.getRenderBox().getCenter();
        this.x = centre.x;
        this.y = centre.y;
        this.z = centre.z;
        this.looping = true;
        this.delay = 0;
        float speed = generator.getShownSpeed();
        this.volume = volumeAt(speed) * 0.5F;
        this.pitch = pitchAt(speed);
    }

    private static float volumeAt(float speed) {
        return 0.3F + 0.7F * speed;
    }

    private static float pitchAt(float speed) {
        return 0.6F + 0.65F * speed;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        float speed = generator.getShownSpeed();
        if (generator.isRemoved() || generator.getLevel() != minecraft.level || generator.getBox() == null || speed < SILENT_BELOW) {
            PLAYING.remove(generator.getBlockPos(), this);
            stop();
            return;
        }
        volume += (volumeAt(speed) - volume) * EASE;
        pitch += (pitchAt(speed) - pitch) * EASE;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
