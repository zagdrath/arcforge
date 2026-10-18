/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

// The Electric Enchanter's floating book, ticked on the client (as the enchanting table's is): it opens while the
// machine works and turns its pages, and closes and settles when it stops. The renderer reads it with partial ticks.
public final class EnchanterBook {
    public int time;
    public float open;
    public float oOpen;
    public float flip;
    public float oFlip;
    private float flipTarget;
    private float flipSpeed;

    public void tick(boolean working, RandomSource random) {
        oOpen = open;
        oFlip = flip;
        time++;
        open = Mth.clamp(open + (working ? 0.1F : -0.1F), 0.0F, 1.0F);
        if (working && random.nextInt(30) == 0) {
            // A new page to turn to, a few either way.
            float old = flipTarget;
            do {
                flipTarget += random.nextInt(4) - random.nextInt(4);
            } while (old == flipTarget);
        }
        float pull = Mth.clamp((flipTarget - flip) * 0.4F, -0.2F, 0.2F);
        flipSpeed += (pull - flipSpeed) * 0.9F;
        flip += flipSpeed;
    }

    public float open(float partialTicks) {
        return Mth.lerp(partialTicks, oOpen, open);
    }

    public float flip(float partialTicks) {
        return Mth.lerp(partialTicks, oFlip, flip);
    }
}
