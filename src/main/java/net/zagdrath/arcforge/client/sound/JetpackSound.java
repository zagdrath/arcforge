/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.zagdrath.arcforge.registry.ModSounds;

// A jetpack's thrust, following its wearer: the steam hiss or the hydrogen roar, louder and higher the harder it
// fires. It fades out over 4 ticks once the jetpack stops (or switches fuel), then stops.
public class JetpackSound extends AbstractTickableSoundInstance {
    public interface Output {
        // 0 to 1, and the exhaust it fires with (0 none, 1 steam, 2 flame).
        float output();

        int exhaust();
    }

    private static final float FADE = 0.25F;

    private final Entity entity;
    private final int exhaust;
    private final Output state;
    private float fade = 1.0F;

    public JetpackSound(Entity entity, int exhaust, Output state) {
        super((exhaust == 2 ? ModSounds.JETPACK_FLAME : ModSounds.JETPACK_STEAM).get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.entity = entity;
        this.exhaust = exhaust;
        this.state = state;
        this.looping = true;
        this.delay = 0;
        tick();
    }

    public int exhaust() {
        return exhaust;
    }

    @Override
    public void tick() {
        float output = state.exhaust() == exhaust && !entity.isRemoved() ? state.output() : 0.0F;
        if (output <= 0.0F) {
            fade -= FADE;
            if (fade <= 0.0F) {
                stop();
                return;
            }
        } else {
            fade = 1.0F;
        }
        float shown = Math.max(output, 0.2F);
        volume = (0.25F + 0.55F * shown) * Mth.clamp(fade, 0.0F, 1.0F);
        pitch = 0.85F + 0.3F * shown;
        x = entity.getX();
        y = entity.getY() + 0.8;
        z = entity.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
