/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer.energy;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

// FE buffer for generators: other mods can extract but never insert. The owning machine fills it with generate().
public class GeneratorEnergyHandler extends SimpleEnergyHandler {
    private final Runnable onChanged;

    public GeneratorEnergyHandler(int capacity, int maxExtract, Runnable onChanged) {
        super(capacity, 0, maxExtract);
        this.onChanged = onChanged;
    }

    // Adds up to the given FE, returning how much actually fit.
    public int generate(int amount) {
        int added = Math.min(Math.max(0, amount), capacity - energy);
        if (added > 0) {
            set(energy + added);
        }
        return added;
    }

    public boolean isFull() {
        return energy >= capacity;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        onChanged.run();
    }
}
