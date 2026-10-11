/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer.energy;

import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

// The store of a Creative Energy Cell (see StorageBlock): always full. It gives any amount without running down and
// takes any amount, destroying it. What goes in and out is still counted for the GUI; the counts follow transactions,
// so a simulated move counts nothing.
public class CreativeEnergyHandler extends TrackingEnergyHandler {
    private final Totals totals = new Totals();

    public CreativeEnergyHandler(int capacity, Runnable onChanged) {
        super(capacity, Integer.MAX_VALUE, onChanged);
        energy = capacity;
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(amount);
        if (amount > 0) {
            totals.updateSnapshots(transaction);
            received = (int) Math.min(Integer.MAX_VALUE, (long) received + amount);
        }
        return amount;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(amount);
        if (amount > 0) {
            totals.updateSnapshots(transaction);
            extracted = (int) Math.min(Integer.MAX_VALUE, (long) extracted + amount);
        }
        return amount;
    }

    // Saved or carried amounts don't matter: it's always full.
    @Override
    public void load(int amount) {}

    @Override
    public void deserialize(ValueInput input) {}

    private class Totals extends SnapshotJournal<int[]> {
        @Override
        protected int[] createSnapshot() {
            return new int[] { received, extracted };
        }

        @Override
        protected void revertToSnapshot(int[] snapshot) {
            received = snapshot[0];
            extracted = snapshot[1];
        }
    }
}
