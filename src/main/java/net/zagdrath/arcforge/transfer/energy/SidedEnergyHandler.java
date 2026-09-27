/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer.energy;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

// View of an energy buffer for one face: input faces only accept FE, output faces only give it.
public class SidedEnergyHandler implements EnergyHandler {
    private final EnergyHandler delegate;
    private final boolean canInsert;
    private final boolean canExtract;

    public SidedEnergyHandler(EnergyHandler delegate, boolean canInsert, boolean canExtract) {
        this.delegate = delegate;
        this.canInsert = canInsert;
        this.canExtract = canExtract;
    }

    @Override
    public long getAmountAsLong() {
        return delegate.getAmountAsLong();
    }

    @Override
    public long getCapacityAsLong() {
        return delegate.getCapacityAsLong();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        return canInsert ? delegate.insert(amount, transaction) : 0;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        return canExtract ? delegate.extract(amount, transaction) : 0;
    }
}
