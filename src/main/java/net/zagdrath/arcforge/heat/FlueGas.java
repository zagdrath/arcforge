/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.heat;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineOutputs;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.tag.ModFluidTags;
import net.zagdrath.arcforge.tag.ModItemTags;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// A burner's flue: while a Flue Gas face (single blocks: the Gas Output side mode, named "Flue Gas" in their Sides tab;
// arrays: a Gas Output port) is set, burning a carbon fuel (#arcforge:carbon_fuels, items or fluids) puts
// flueGas.carbonDioxidePerThousandHu mB of Carbon Dioxide into this tank for every 1,000 HU made. Hydrogen and other
// fuels outside the tags give none. What doesn't fit is vented, so a full flue never stops a burner, and a burner with
// no Flue Gas face never calls emit, so it runs exactly as it did before.
public final class FlueGas {
    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> output;
    // Carbon Dioxide owed below a whole mB, carried to the next tick.
    private double owed;
    // mB made in the last tick it burnt (for GUIs and Jade).
    private int madeLastTick;

    public FlueGas(int capacity, Runnable onChanged) {
        this.tank = new FilteredFluidTank(capacity, resource -> resource.getFluid() == ModFluids.CARBON_DIOXIDE.get(), onChanged);
        this.output = new AutomationResourceHandler<>(tank, index -> false, index -> true);
    }

    public static boolean isCarbonFuel(@Nullable Item item) {
        return item != null && item.getDefaultInstance().is(ModItemTags.CARBON_FUELS);
    }

    public static boolean isCarbonFuel(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItemTags.CARBON_FUELS);
    }

    public static boolean isCarbonFuel(@Nullable Fluid fluid) {
        return fluid != null && fluid.defaultFluidState().is(ModFluidTags.CARBON_FUELS);
    }

    // The Carbon Dioxide (mB, fractional) that this much heat from a carbon fuel gives off.
    public static double carbonDioxideFor(double hu) {
        return Math.max(0.0, hu) / 1_000.0 * ArcforgeConfig.FLUE_GAS_PER_THOUSAND_HU.getAsDouble();
    }

    // This tick's heat (HU) from the fuel burning; carbon says whether that fuel is a carbon fuel. Returns the mB that
    // went into the tank.
    public int emit(double hu, boolean carbon) {
        if (!carbon || hu <= 0) {
            madeLastTick = 0;
            return 0;
        }
        owed += carbonDioxideFor(hu);
        int whole = (int) owed;
        owed -= whole;
        madeLastTick = whole;
        if (whole <= 0) {
            return 0;
        }
        int fit = Math.min(whole, tank.getSpace());
        if (fit <= 0) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = tank.insert(0, FluidResource.of(ModFluids.CARBON_DIOXIDE.get()), fit, tx);
            tx.commit();
            return inserted;
        }
    }

    // Single-block burners: whether any face is set to Flue Gas (the Gas Output side mode).
    public static boolean hasFlueFace(SideConfig sides, Direction facing) {
        for (Direction direction : Direction.values()) {
            if (sides.get(facing, direction) == SideMode.GAS_OUTPUT) {
                return true;
            }
        }
        return false;
    }

    // Single-block burners: pushes the flue gas out of every Flue Gas face, up to flueGas.outputRate mB in all.
    public void push(ServerLevel level, BlockPos pos, Direction facing, SideConfig sides, MachineOutputs outputs) {
        int budget = ArcforgeConfig.FLUE_GAS_OUTPUT_RATE.getAsInt();
        for (Direction direction : Direction.values()) {
            if (budget <= 0 || tank.getAmount() <= 0) {
                return;
            }
            if (sides.get(facing, direction) == SideMode.GAS_OUTPUT) {
                budget -= outputs.pushFluid(level, pos, direction, output, budget);
            }
        }
    }

    // Multiblocks: pushes what the flue holds into what touches their Flue Gas ports.
    public void pushTo(List<BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction>> targets) {
        for (BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> target : targets) {
            if (tank.getAmount() <= 0) {
                return;
            }
            ResourceHandler<FluidResource> handler = target.getCapability();
            if (handler != null) {
                ResourceHandlerUtil.move(output, handler, resource -> true, tank.getAmount(), null);
            }
        }
    }

    // A tick without burning.
    public void idle() {
        madeLastTick = 0;
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    // Extract-only: what Flue Gas faces give out.
    public ResourceHandler<FluidResource> getOutput() {
        return output;
    }

    public int getMadeLastTick() {
        return madeLastTick;
    }

    public void load(ValueInput input) {
        tank.deserialize(input.childOrEmpty("flue_gas"));
        owed = input.getDoubleOr("flue_gas_owed", 0.0);
    }

    public void save(ValueOutput output) {
        tank.serialize(output.child("flue_gas"));
        output.putDouble("flue_gas_owed", owed);
    }
}
