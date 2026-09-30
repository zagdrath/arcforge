/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.farming;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Hydroponic Cell: no soil, the roots in Nutrient Solution under a grow lamp. Grows at hydroponicCell.speed with FE
// and Nutrient Solution, and also grows what only it can (saplings, flowers, Hops: the hydroponic_only recipes). With
// Carbon Dioxide in its gas tank it grows co2Bonus times faster, using co2PerTick mB while it grows. Input faces take
// Nutrient Solution from pipes and Carbon Dioxide from gas conduits. Takes fertilizer, Speed and Energy upgrades.
public class HydroponicCellBlockEntity extends ClocheBlockEntity {
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    private final FilteredFluidTank co2;
    private boolean boosted;

    public HydroponicCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.HYDROPONIC_CELL.get(), pos, state, Kind.HYDROPONIC_CELL, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES,
                ArcforgeConfig.HYDROPONIC_CELL_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.HYDROPONIC_CELL_MAX_INPUT.getAsInt(),
                ArcforgeConfig.HYDROPONIC_CELL_NUTRIENT_CAPACITY.getAsInt(), resource -> resource.getFluid() == ModFluids.NUTRIENT_SOLUTION.get());
        this.co2 = new FilteredFluidTank(ArcforgeConfig.HYDROPONIC_CELL_CO2_CAPACITY.getAsInt(),
                resource -> resource.getFluid() == ModFluids.CARBON_DIOXIDE.get(), this::setChanged);
    }

    @Override
    protected double speed() {
        return ArcforgeConfig.HYDROPONIC_CELL_SPEED.getAsDouble() * speedMultiplier();
    }

    @Override
    protected int fluidPerHarvest() {
        return ArcforgeConfig.HYDROPONIC_CELL_NUTRIENT_PER_HARVEST.getAsInt();
    }

    @Override
    protected MachineStatus noFluidStatus() {
        return MachineStatus.NO_NUTRIENTS;
    }

    // FE/t while growing: Speed draws it faster, Energy cuts it.
    @Override
    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.HYDROPONIC_CELL_ENERGY_PER_TICK.getAsInt() * speedMultiplier()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    // Carbon Dioxide, while there is enough for this tick.
    @Override
    protected double tickBonus(ServerLevel level) {
        int needed = ArcforgeConfig.HYDROPONIC_CELL_CO2_PER_TICK.getAsInt();
        boosted = co2.getAmount() >= needed;
        if (!boosted) {
            return 1.0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            co2.extract(0, co2.getResource(0), needed, tx);
            tx.commit();
        }
        return ArcforgeConfig.HYDROPONIC_CELL_CO2_BONUS.getAsDouble();
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boosted = false;
        super.serverTick(level, pos, state);
    }

    // Whether Carbon Dioxide sped it up last tick.
    public boolean isBoosted() {
        return boosted;
    }

    @Override
    protected void moveOutputs(ServerLevel level) {
        autoEject(level, outputHandler());
    }

    // Nutrient Solution and Carbon Dioxide go in through the same faces; each tank takes only its own.
    @Override
    protected ResourceHandler<FluidResource> inputTanks() {
        return new CombinedResourceHandler<>(tank, co2);
    }

    @Override
    protected int gasAmount() {
        return co2.getAmount();
    }

    @Override
    protected int gasCapacity() {
        return co2.getCapacity();
    }

    public FilteredFluidTank getCo2Tank() {
        return co2;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        co2.deserialize(input.childOrEmpty("co2"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        co2.serialize(output.child("co2"));
    }
}
