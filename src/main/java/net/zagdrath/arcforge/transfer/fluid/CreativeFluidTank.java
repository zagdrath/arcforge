/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer.fluid;

import java.util.function.Predicate;

import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

// The tank of a Creative Fluid Tank or Pressurized Cylinder (see StorageBlock). The first fluid put in fills it to
// capacity for good. Taking from it never runs it down, and more of the same fluid is taken and destroyed; any other
// fluid is refused until it's cleared.
public class CreativeFluidTank extends FilteredFluidTank {
    public CreativeFluidTank(int capacity, Predicate<FluidResource> filter, Runnable onChanged) {
        super(capacity, filter, onChanged);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (getAmount() == 0) {
            return super.insert(index, resource, getCapacity(), transaction) > 0 ? amount : 0;
        }
        return getResource(0).equals(resource) ? amount : 0;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return getAmount() > 0 && getResource(0).equals(resource) ? amount : 0;
    }

    // Loads saved contents: any of a fluid fills it.
    @Override
    public void set(int index, FluidResource resource, int amount) {
        super.set(index, resource, resource.isEmpty() || amount == 0 ? 0 : getCapacity());
    }

    public void clear() {
        set(0, FluidResource.EMPTY, 0);
    }
}
