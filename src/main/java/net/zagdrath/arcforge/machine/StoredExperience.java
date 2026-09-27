/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

// Smelting XP a machine holds until a player takes its output (or it is broken), like a vanilla furnace.
// A fractional remainder pays out one point with that chance.
public class StoredExperience {
    private final Runnable onChanged;
    private float stored;

    public StoredExperience(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public float get() {
        return stored;
    }

    public void add(float amount) {
        if (amount > 0) {
            stored += amount;
            onChanged.run();
        }
    }

    // Drops everything held as experience orbs at pos.
    public void award(ServerLevel level, Vec3 pos) {
        if (stored <= 0) {
            return;
        }
        int amount = Mth.floor(stored);
        float fraction = Mth.frac(stored);
        if (fraction != 0.0F && level.getRandom().nextFloat() < fraction) {
            amount++;
        }
        stored = 0;
        onChanged.run();
        ExperienceOrb.award(level, pos, amount);
    }

    public void serialize(ValueOutput output) {
        output.putFloat("experience", stored);
    }

    public void deserialize(ValueInput input) {
        stored = input.getFloatOr("experience", 0.0F);
    }
}
