/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// A tank for steam of any grade that holds one grade at a time. Steam of a lower grade arriving turns
// what's stored down to it (the pressure drops, the amount stays); steam of a higher grade is taken in
// at the stored grade.
public class SteamTank extends FilteredFluidTank {
    public SteamTank(int capacity, Runnable onChanged) {
        super(capacity, BoilerCore::isSteam, onChanged);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        FluidResource stored = getResource(0);
        int held = getAmountAsInt(0);
        SteamGrade incoming = SteamGrade.of(resource);
        SteamGrade current = SteamGrade.of(stored);
        if (held == 0 || incoming == null || current == null || stored.equals(resource)) {
            return super.insert(index, resource, amount, transaction);
        }
        int accepted = Math.min(amount, getCapacity() - held);
        if (accepted <= 0) {
            return 0;
        }
        SteamGrade result = incoming.ordinal() < current.ordinal() ? incoming : current;
        super.extract(0, stored, held, transaction);
        super.insert(0, result.resource(), held + accepted, transaction);
        return accepted;
    }
}
