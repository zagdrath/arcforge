/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer.fluid;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

// Input tanks (two or more) that sort what comes in: a fluid goes into the tank that already holds it, otherwise
// into an empty tank, but never into an empty one while another holds it. So each fluid (water, acid, lye) keeps a
// tank of its own, one fluid is never split across two, and one more than there are tanks is refused while all are
// in use. Taking out works from any tank, A first.
public class InputTankRouter implements ResourceHandler<FluidResource> {
    private final FilteredFluidTank[] tanks;

    public InputTankRouter(FilteredFluidTank... tanks) {
        this.tanks = tanks.clone();
    }

    // Whether this fluid may go into that tank now: it's the tank's own fluid, or the tank is empty and no other
    // tank holds it.
    private boolean accepts(int index, FluidResource resource) {
        FilteredFluidTank tank = tanks[index];
        if (tank.getAmount() > 0) {
            return tank.getResource(0).equals(resource);
        }
        for (int other = 0; other < tanks.length; other++) {
            if (other != index && tanks[other].getAmount() > 0 && tanks[other].getResource(0).equals(resource)) {
                return false;
            }
        }
        return true;
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
