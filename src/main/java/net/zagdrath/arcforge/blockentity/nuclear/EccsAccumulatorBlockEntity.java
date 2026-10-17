/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// An ECCS Accumulator: a tank of Water kept under nitrogen pressure beside a reactor vessel, part of its Emergency
// Core Cooling. It needs no power: when the vessel's coolant falls below nuclear.safety.eccsLevel the reactor lets it
// in, nuclear.safety.accumulatorRate mB a tick (see FissionReactorBlockEntity). Filled with Water through its faces
// (pipes, buckets).
public class EccsAccumulatorBlockEntity extends NuclearBlockEntity implements FluidInteractable {
    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> input;

    public EccsAccumulatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ECCS_ACCUMULATOR.get(), pos, state);
        this.tank = new FilteredFluidTank(ArcforgeConfig.ECCS_ACCUMULATOR_CAPACITY.getAsInt(), resource -> resource.is(Fluids.WATER), this::setChanged);
        this.input = new AutomationResourceHandler<>(tank, index -> true, index -> false);
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    public ResourceHandler<FluidResource> getFluidHandler() {
        return input;
    }

    // Lets up to max mB out into the vessel; returns how much.
    public int discharge(int max) {
        int out = Math.min(max, Math.min(tank.getAmount(), ArcforgeConfig.ECCS_ACCUMULATOR_RATE.getAsInt()));
        if (out > 0) {
            int left = tank.getAmount() - out;
            tank.set(0, left > 0 ? tank.getResource(0) : FluidResource.EMPTY, left);
        }
        return out;
    }

    // A held bucket of Water fills it.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return input;
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tank.deserialize(in.childOrEmpty("tank"));
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        tank.serialize(out.child("tank"));
    }
}
