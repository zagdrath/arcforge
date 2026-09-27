/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer.energy;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

// FE buffer that totals how much came in and went out since the last call to resetTotals(),
// for showing live input/output rates. Each insert/extract is capped at the I/O rate.
public class TrackingEnergyHandler extends SimpleEnergyHandler {
    private final Runnable onChanged;
    private int received;
    private int extracted;

    public TrackingEnergyHandler(int capacity, int maxTransfer, Runnable onChanged) {
        super(capacity, maxTransfer, maxTransfer);
        this.onChanged = onChanged;
    }

    public int getReceived() {
        return received;
    }

    public int getExtracted() {
        return extracted;
    }

    public void resetTotals() {
        received = 0;
        extracted = 0;
    }

    public int getRate() {
        return maxInsert;
    }

    // Loads a stored amount (e.g. from the item component) without counting it as input.
    public void load(int amount) {
        energy = Math.clamp(amount, 0, capacity);
        onChanged.run();
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        if (energy > previousAmount) {
            received += energy - previousAmount;
        } else {
            extracted += previousAmount - energy;
        }
        onChanged.run();
    }
}
