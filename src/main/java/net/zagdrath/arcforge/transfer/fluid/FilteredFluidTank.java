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

    public int getCapacity() {
        return capacity;
    }

    public int getSpace() {
        return Math.max(0, capacity - getAmount());
    }

    public boolean contains(Fluid fluid) {
        return getResource(0).getFluid() == fluid;
    }
}
