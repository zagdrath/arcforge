/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer.energy;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

// An FE store held as a long (the Battery Array's, which can pass the int limit), with a budget for what it may take in
// and give out before the owner resets them (once a tick), and totals of what came in and went out since the last
// resetTotals, for live rates. Transactions roll back the amount, the budgets and the totals together.
public class LongEnergyStore extends SnapshotJournal<LongEnergyStore.Snapshot> implements EnergyHandler {
    record Snapshot(long amount, long inputLeft, long outputLeft, long received, long extracted) {}

    private final Runnable onChanged;
    private long amount;
    private long capacity;
    private long inputLeft;
    private long outputLeft;
    private long received;
    private long extracted;

    public LongEnergyStore(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    @Override
    public long getAmountAsLong() {
        return amount;
    }

    @Override
    public long getCapacityAsLong() {
        return capacity;
    }

    // A new capacity; the amount is cut to it.
    public void setCapacity(long capacity) {
        this.capacity = Math.max(0, capacity);
        if (amount > this.capacity) {
            amount = this.capacity;
            onChanged.run();
        }
    }

    // Sets the amount directly (loading, forming), not counted as input or output.
    public void set(long amount) {
        long clamped = Math.clamp(amount, 0, capacity);
        if (clamped != this.amount) {
            this.amount = clamped;
            onChanged.run();
        }
    }

    // How much may come in and go out from now until the next call (each way).
    public void setBudgets(long input, long output) {
        inputLeft = Math.max(0, input);
        outputLeft = Math.max(0, output);
    }

    public long getReceived() {
        return received;
    }

    public long getExtracted() {
        return extracted;
    }

    public void resetTotals() {
        received = 0;
        extracted = 0;
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        if (amount <= 0) {
            return 0;
        }
        int accepted = (int) Math.min(amount, Math.min(inputLeft, capacity - this.amount));
        if (accepted > 0) {
            updateSnapshots(transaction);
            this.amount += accepted;
            inputLeft -= accepted;
            received += accepted;
        }
        return accepted;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        if (amount <= 0) {
            return 0;
        }
        int given = (int) Math.min(amount, Math.min(outputLeft, this.amount));
        if (given > 0) {
            updateSnapshots(transaction);
            this.amount -= given;
            outputLeft -= given;
            extracted += given;
        }
        return given;
    }

    @Override
    protected Snapshot createSnapshot() {
        return new Snapshot(amount, inputLeft, outputLeft, received, extracted);
    }

    @Override
    protected void revertToSnapshot(Snapshot snapshot) {
        amount = snapshot.amount();
        inputLeft = snapshot.inputLeft();
        outputLeft = snapshot.outputLeft();
        received = snapshot.received();
        extracted = snapshot.extracted();
    }

    @Override
    protected void onRootCommit(Snapshot original) {
        if (original.amount() != amount) {
            onChanged.run();
        }
    }
}
