/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer.fluid;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

// Two input tanks that sort what comes in: a fluid goes into the tank that already holds it, otherwise into an
// empty tank, but never into an empty one while the other holds it. So two fluids (water and acid) each keep a
// tank of their own, one fluid is never split across both, and a third is refused while both are in use.
// Taking out works from either tank, A first.
public class InputTankRouter implements ResourceHandler<FluidResource> {
    private final FilteredFluidTank[] tanks;

    public InputTankRouter(FilteredFluidTank tankA, FilteredFluidTank tankB) {
        this.tanks = new FilteredFluidTank[] { tankA, tankB };
    }

    // Whether this fluid may go into that tank now.
    private boolean accepts(int index, FluidResource resource) {
        FilteredFluidTank tank = tanks[index];
        FilteredFluidTank other = tanks[1 - index];
        if (tank.getAmount() > 0) {
            return tank.getResource(0).equals(resource);
        }
        return other.getAmount() == 0 || !other.getResource(0).equals(resource);
    }

    @Override
    public int size() {
        return tanks.length;
    }

    @Override
    public FluidResource getResource(int index) {
        return tanks[index].getResource(0);
    }

    @Override
    public long getAmountAsLong(int index) {
        return tanks[index].getAmountAsLong(0);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return tanks[index].getCapacityAsLong(0, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return tanks[index].isValid(0, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return accepts(index, resource) ? tanks[index].insert(0, resource, amount, transaction) : 0;
    }

    // The tank already holding it first, then an empty one.
    @Override
    public int insert(FluidResource resource, int amount, TransactionContext transaction) {
        for (int index = 0; index < tanks.length; index++) {
            if (tanks[index].getAmount() > 0 && tanks[index].getResource(0).equals(resource)) {
                return tanks[index].insert(0, resource, amount, transaction);
            }
        }
        for (int index = 0; index < tanks.length; index++) {
            if (accepts(index, resource)) {
                return tanks[index].insert(0, resource, amount, transaction);
            }
        }
        return 0;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return tanks[index].extract(0, resource, amount, transaction);
    }
}
