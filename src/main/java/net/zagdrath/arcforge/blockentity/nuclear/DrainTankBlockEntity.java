/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.nuclear.RadiationField;
import net.zagdrath.arcforge.nuclear.RadiationSource;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// A molten salt reactor's Drain Tank: a squat, heavily shielded tank under its Freeze Plug. When the plug melts the
// core's Fuel Salt drains into it (see FissionReactorBlockEntity), spread thin in a shape that can't go critical, and
// cools there; SHIELDING of its radiation gets out. Its salt is pumped back out of its faces (to the reactor's Input
// ports) once the plug has frozen again.
public class DrainTankBlockEntity extends NuclearBlockEntity implements RadiationSource {
    public static final int CAPACITY = 1_000_000;
    public static final double SHIELDING = 0.002;

    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> output;

    public DrainTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.DRAIN_TANK.get(), pos, state);
        this.tank = new FilteredFluidTank(CAPACITY, resource -> resource.is(ModFluids.FUEL_SALT.get()), this::setChanged);
        this.output = new AutomationResourceHandler<>(tank, index -> false, index -> true);
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    public ResourceHandler<FluidResource> getFluidHandler() {
        return output;
    }

    // Takes in up to amount mB of the reactor's salt; returns how much.
    public int fill(int amount) {
        int moved = Math.min(amount, tank.getSpace());
        if (moved > 0) {
            tank.set(0, FluidResource.of(ModFluids.FUEL_SALT.get()), tank.getAmount() + moved);
        }
        return moved;
    }

    @Override
    public double radiationRate() {
        return tank.getAmount() > 0 ? RadiationField.rate(tank.getResource(0).toStack(tank.getAmount())) * SHIELDING : 0.0;
    }

    @Override
    public Vec3 radiationCentre() {
        return Vec3.atCenterOf(worldPosition);
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
