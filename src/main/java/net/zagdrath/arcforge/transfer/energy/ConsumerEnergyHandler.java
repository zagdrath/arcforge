/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer.energy;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

// FE buffer for machines that use power: other mods and cables can insert but never extract. The
// owning machine draws from it with consume().
public class ConsumerEnergyHandler extends SimpleEnergyHandler {
    private final Runnable onChanged;

    public ConsumerEnergyHandler(int capacity, int maxInsert, Runnable onChanged) {
        super(capacity, maxInsert, 0);
        this.onChanged = onChanged;
    }

    // Takes the given FE if it's all there; returns whether it was.
    public boolean consume(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (energy < amount) {
            return false;
        }
        set(energy - amount);
        return true;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        onChanged.run();
    }
}
