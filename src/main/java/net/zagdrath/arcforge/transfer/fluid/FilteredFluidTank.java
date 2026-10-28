/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer.fluid;

import java.util.function.Predicate;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

// Single-tank fluid handler that only accepts fluids matching a filter.
public class FilteredFluidTank extends FluidStacksResourceHandler {
    private final Predicate<FluidResource> filter;
    private final Runnable onChanged;
    private boolean ventGasOverflow;

    public FilteredFluidTank(int capacity, Predicate<FluidResource> filter, Runnable onChanged) {
        super(1, capacity);
        this.filter = filter;
        this.onChanged = onChanged;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return filter.test(resource);
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        onChanged.run();
    }

    public int getAmount() {
        return getAmountAsInt(0);
    }

    public void setVentingGas(boolean enabled) {
        ventGasOverflow = enabled;
    }

    public boolean canVent(FluidResource resource) {
        return ventGasOverflow && !resource.isEmpty() && net.zagdrath.arcforge.steam.Gases.isGas(resource) && isValid(0, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
        int stored = super.insert(index, resource, amount, transaction);
        // Un-stored gas is discarded as part of the operation. Liquid outputs still require space.
        return index == 0 && canVent(resource) ? amount : stored;
    }

    public int getCapacity() {
        return capacity;
    }

    // For tanks that grow and shrink with their machine. Fluid already above a lowered capacity stays until drained.
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getSpace() {
        return Math.max(0, capacity - getAmount());
    }

    public boolean contains(Fluid fluid) {
        return getResource(0).getFluid() == fluid;
    }
}
