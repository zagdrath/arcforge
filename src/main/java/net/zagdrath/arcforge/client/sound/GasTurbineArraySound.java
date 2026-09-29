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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.blockentity.multiblock.GasTurbineArrayBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.registry.ModParticleTypes;
import net.zagdrath.arcforge.registry.ModSounds;

// The Gas Turbine Array's running sound: a loop from the middle of the turbine while the rotor turns faster
// than 2% of full speed, its volume (0.2-1.0) and pitch (0.6-1.4, so the whine climbs from about 720 Hz to
// 1.7 kHz) following the rotor and easing as it spools and coasts. Also puffs heat haze out of the exhaust
// while it vents to the air.
public class GasTurbineArraySound extends AbstractTickableSoundInstance {
    private static final Map<BlockPos, GasTurbineArraySound> PLAYING = new HashMap<>();
    private static final float SILENT_BELOW = 0.02F;
    private static final float EASE = 0.15F;
    // Haze particles a tick at full exhaust, and how fast they drift out.
    private static final float HAZE_PER_TICK = 2.0F;
    private static final double HAZE_DRIFT = 0.05;

    private final GasTurbineArrayBlockEntity turbine;

    // Called every client tick by the turbine's master (see GasTurbineArrayBlockEntity.clientHook).
    public static void clientTick(GasTurbineArrayBlockEntity turbine) {
        spawnHaze(turbine);
        if (speed(turbine) < SILENT_BELOW) {
            return;
        }
        BlockPos pos = turbine.getBlockPos();
        GasTurbineArraySound sound = PLAYING.get(pos);
        if (sound != null && sound.turbine == turbine && !sound.isStopped()) {
            return;
        }
        sound = new GasTurbineArraySound(turbine);
        PLAYING.put(pos.immutable(), sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    // Venting exhaust shimmers out of the exhaust end: up to HAZE_PER_TICK a tick, scaled by the exhaust.
    private static void spawnHaze(GasTurbineArrayBlockEntity turbine) {
        ShellStructure.Shell shell = turbine.getShell();
        Level level = turbine.getLevel();
        if (shell == null || level == null || !turbine.isSyncedVenting() || turbine.getSyncedExhaustHu() <= 0) {
            return;
        }
        double fullExhaust = turbine.maxHuPerTick() * ArcforgeConfig.GAS_TURBINE_EXHAUST_FRACTION.getAsDouble();
        float count = HAZE_PER_TICK * (float) Mth.clamp(turbine.getSyncedExhaustHu() / Math.max(1.0, fullExhaust), 0.0, 1.0);
        RandomSource random = level.getRandom();
        Direction out = turbine.intakeFacing().getOpposite();
        Vec3 face = Vec3.atCenterOf(shell.endCenter(turbine.getIntakeEnd().opposite())).add(Vec3.atLowerCornerOf(out.getUnitVec3i()).scale(0.55));
        while (count > 0) {
            if (count < 1 && random.nextFloat() >= count) {
                break;
            }
            count--;
            double spreadA = (random.nextDouble() - 0.5) * 0.9;
            double spreadB = (random.nextDouble() - 0.5) * 0.9;
            double x = face.x + (out.getAxis() == Direction.Axis.X ? 0 : spreadA);
            double z = face.z + (out.getAxis() == Direction.Axis.Z ? 0 : spreadA);
            level.addParticle(ModParticleTypes.HEAT_HAZE.get(), x, face.y + spreadB, z,
                    out.getStepX() * HAZE_DRIFT, 0.0, out.getStepZ() * HAZE_DRIFT);
        }
    }

    private GasTurbineArraySound(GasTurbineArrayBlockEntity turbine) {
        super(ModSounds.GAS_TURBINE_ARRAY_RUN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.turbine = turbine;
        Vec3 centre = turbine.getRenderBox().getCenter();
        this.x = centre.x;
        this.y = centre.y;
        this.z = centre.z;
        this.looping = true;
        this.delay = 0;
        float speed = speed(turbine);
        this.volume = volumeAt(speed) * 0.5F;
        this.pitch = pitchAt(speed);
    }

    private static float speed(GasTurbineArrayBlockEntity turbine) {
        return Mth.clamp(turbine.getSyncedRpm() / (float) GasTurbineArrayBlockEntity.maxRpm(), 0.0F, 1.0F);
    }

    private static float volumeAt(float speed) {
        return 0.2F + 0.8F * speed;
    }

    private static float pitchAt(float speed) {
        return 0.6F + 0.8F * speed;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        float speed = speed(turbine);
        if (turbine.isRemoved() || turbine.getLevel() != minecraft.level || !turbine.isMaster() || speed < SILENT_BELOW) {
            PLAYING.remove(turbine.getBlockPos(), this);
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
