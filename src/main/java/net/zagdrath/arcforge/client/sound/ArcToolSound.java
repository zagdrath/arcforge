/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.zagdrath.arcforge.client.ArcToolClient;
import net.zagdrath.arcforge.item.tool.ArcToolItem;
import net.zagdrath.arcforge.registry.ModSounds;

// An Arc Drill's whine or Arc Saw's buzz, following the player using it. It spins up over a few ticks (volume and
// pitch rising together), and winds down and stops once they stop mining.
public class ArcToolSound extends AbstractTickableSoundInstance {
    private static final float SPIN_UP = 0.2F;
    private static final float WIND_DOWN = 0.15F;

    private final Player player;
    private final ArcToolItem.Kind kind;
    private float spin;

    public ArcToolSound(Player player, ArcToolItem.Kind kind) {
        super((kind == ArcToolItem.Kind.DRILL ? ModSounds.ARC_DRILL_RUN : ModSounds.ARC_SAW_RUN).get(), SoundSource.PLAYERS,
                SoundInstance.createUnseededRandom());
        this.player = player;
        this.kind = kind;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.pitch = 0.8F;
        follow();
    }

    private void follow() {
        x = player.getX();
        y = player.getEyeY() - 0.3;
        z = player.getZ();
    }

    @Override
    public void tick() {
        boolean running = !player.isRemoved() && ArcToolClient.isRunning(player)
                && player.getMainHandItem().getItem() instanceof ArcToolItem tool && tool.kind() == kind;
        spin = Math.clamp(spin + (running ? SPIN_UP : -WIND_DOWN), 0.0F, 1.0F);
        volume = 0.75F * spin;
        pitch = 0.8F + 0.2F * spin;
        follow();
        if (!running && spin <= 0.0F) {
            stop();
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
