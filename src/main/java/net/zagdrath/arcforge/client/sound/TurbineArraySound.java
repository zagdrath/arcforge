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
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.registry.ModSounds;

// The Steam Turbine Array's running sound: a loop from the middle of the turbine whose pitch and volume
// follow the rotor, easing as it spins up and coasts down. It starts once the rotor turns and stops when
// the rotor stops, the turbine breaks, or the player leaves the level.
public class TurbineArraySound extends AbstractTickableSoundInstance {
    private static final Map<BlockPos, TurbineArraySound> PLAYING = new HashMap<>();
    // Below this share of full speed, the rotor is silent.
    private static final float SILENT_BELOW = 0.01F;
    private static final float MIN_PITCH = 0.55F, MAX_PITCH = 1.6F;
    private static final float MIN_VOLUME = 0.25F, MAX_VOLUME = 1.0F;
    // How fast pitch and volume follow the rotor (a share per tick).
    private static final float EASE = 0.15F;

    private final SteamTurbineArrayBlockEntity turbine;

    // Called every client tick by the turbine's master (see SteamTurbineArrayBlockEntity.clientSoundHook).
    public static void keepPlaying(SteamTurbineArrayBlockEntity turbine) {
        if (speed(turbine) < SILENT_BELOW) {
            return;
        }
        BlockPos pos = turbine.getBlockPos();
        TurbineArraySound sound = PLAYING.get(pos);
        if (sound != null && sound.turbine == turbine && !sound.isStopped()) {
            return;
        }
        sound = new TurbineArraySound(turbine);
        PLAYING.put(pos.immutable(), sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    private TurbineArraySound(SteamTurbineArrayBlockEntity turbine) {
        super(ModSounds.TURBINE_ARRAY_RUN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
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

    // The rotor's share of its full speed.
    private static float speed(SteamTurbineArrayBlockEntity turbine) {
        return Mth.clamp(turbine.getSyncedRpm() / (float) SteamTurbineArrayBlockEntity.maxRpm(), 0.0F, 1.0F);
    }

    private static float volumeAt(float speed) {
        return Mth.lerp(Mth.sqrt(speed), MIN_VOLUME, MAX_VOLUME);
    }

    private static float pitchAt(float speed) {
        return Mth.lerp(speed, MIN_PITCH, MAX_PITCH);
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
