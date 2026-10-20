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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.blockentity.multiblock.NuclearSteamTurbineBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.registry.ModSounds;

// The Steam Turbine Array's and the Nuclear Steam Turbine's running sound: a loop from the middle of the turbine whose
// pitch and volume follow the rotor, easing as it spins up and coasts down (the nuclear set's bigger rotor a little
// lower). It starts once the rotor turns and stops when the rotor stops, the turbine breaks, or the player leaves the
// level.
public class TurbineArraySound extends AbstractTickableSoundInstance {
    private static final Map<BlockPos, TurbineArraySound> PLAYING = new HashMap<>();
    // Below this share of full speed, the rotor is silent.
    private static final float SILENT_BELOW = 0.01F;
    private static final float MIN_PITCH = 0.55F, MAX_PITCH = 1.6F;
    private static final float MIN_VOLUME = 0.25F, MAX_VOLUME = 1.0F;
    // How fast pitch and volume follow the rotor (a share per tick).
    private static final float EASE = 0.15F;

    private static final float NUCLEAR_PITCH = 0.8F;

    private final ShellMultiblockBlockEntity turbine;

    // Called every client tick by the turbine's master (see SteamTurbineArrayBlockEntity.clientSoundHook and
    // NuclearSteamTurbineBlockEntity.clientSoundHook).
    public static void keepPlaying(ShellMultiblockBlockEntity turbine) {
        if (speed(turbine) < SILENT_BELOW) {
            return;
        }
        BlockPos pos = turbine.getBlockPos();
        TurbineArraySound sound = PLAYING.get(pos);
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        // Asks the sound engine, not just the sound: a sound it refused or dropped (no free channel, all sounds
        // stopped on a resource reload) never ticks again, so it would never be marked stopped and never replaced.
        if (sound != null && sound.turbine == turbine && !sound.isStopped() && sounds.isActive(sound)) {
            return;
        }
        if (sound != null) {
            sound.stop();
        }
        sound = new TurbineArraySound(turbine);
        PLAYING.put(pos.immutable(), sound);
        sounds.play(sound);
    }

    private TurbineArraySound(ShellMultiblockBlockEntity turbine) {
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
        this.pitch = pitchAt(turbine, speed);
    }

    // The rotor's share of its full speed.
    private static float speed(ShellMultiblockBlockEntity turbine) {
        if (turbine instanceof NuclearSteamTurbineBlockEntity nuclear) {
            return Mth.clamp(nuclear.getSyncedRpm() / (float) NuclearSteamTurbineBlockEntity.maxRpm(), 0.0F, 1.0F);
        }
        return turbine instanceof SteamTurbineArrayBlockEntity array
                ? Mth.clamp(array.getSyncedRpm() / (float) SteamTurbineArrayBlockEntity.maxRpm(), 0.0F, 1.0F) : 0.0F;
    }

    private static float volumeAt(float speed) {
        return Mth.lerp(Mth.sqrt(speed), MIN_VOLUME, MAX_VOLUME);
    }

    private static float pitchAt(ShellMultiblockBlockEntity turbine, float speed) {
        return Mth.lerp(speed, MIN_PITCH, MAX_PITCH) * (turbine instanceof NuclearSteamTurbineBlockEntity ? NUCLEAR_PITCH : 1.0F);
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
        pitch += (pitchAt(turbine, speed) - pitch) * EASE;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
