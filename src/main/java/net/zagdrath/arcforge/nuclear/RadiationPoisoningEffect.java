/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

// Radiation Poisoning, I to IV (amplifier 0 to 3): what a dose of radiation does to a body. RadiationDose gives it for the
// dose built up (a step more while the dose rate is acute) and does its harm on its own clock, so the effect itself only
// shows: its icon, a sickly green swirl, and its level in the inventory.
public class RadiationPoisoningEffect extends MobEffect {
    public static final int COLOR = 0x6BD43A;

    public RadiationPoisoningEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
