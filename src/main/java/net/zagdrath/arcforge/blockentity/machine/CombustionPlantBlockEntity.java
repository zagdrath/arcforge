/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.PowerGeneration;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.BurnerMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.upgrade.UpgradeType;
import net.zagdrath.arcforge.transfer.energy.GeneratorEnergyHandler;

// Burns coal, charcoal and coal blocks straight into FE: quick and simple, but only half the FE per
// coal that a Firebox feeding a hot Thermoelectric Plant gets. Fuel burns at twice furnace speed.
public class CombustionPlantBlockEntity extends BurnerBlockEntity {
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY, SideMode.GAS_OUTPUT);

    private final GeneratorEnergyHandler energy;

    public CombustionPlantBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.COMBUSTION_PLANT.get(), pos, state, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new GeneratorEnergyHandler(
                PowerGeneration.cap(ArcforgeConfig.COMBUSTION_PLANT_ENERGY_CAPACITY.getAsInt()),
                PowerGeneration.cap(ArcforgeConfig.COMBUSTION_PLANT_MAX_OUTPUT.getAsInt()),
                this::setChanged);
    }

    // Speed upgrades burn fuel faster and make FE faster, so the FE per fuel item stays the same.
    @Override
    protected double burnSpeed() {
        return ArcforgeConfig.COMBUSTION_PLANT_BURN_SPEED.getAsDouble() * speedMultiplier();
    }

    @Override
    protected int produce() {
        return energy.generate(energyPerTick());
    }

    // FE/t while burning: faster with Speed upgrades, more per fuel item with Energy upgrades, times the power
    // multiplier (see PowerGeneration).
    private int energyPerTick() {
        return PowerGeneration.fe(ArcforgeConfig.COMBUSTION_PLANT_ENERGY_PER_TICK.getAsInt() * speedMultiplier()
                * UpgradeType.outputMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    // It makes FE, not heat: each FE counts as flueGas.combustionPlantHuPerFe HU (2: a coal gives the same Carbon Dioxide
    // as in a Firebox).
    @Override
    protected double flueHeat(int produced) {
        return produced * ArcforgeConfig.FLUE_GAS_COMBUSTION_PLANT_HU_PER_FE.getAsDouble();
    }

    @Override
    protected boolean isBufferFull() {
        return energy.isFull();
    }

    @Override
    protected void pushOutput(ServerLevel level, BlockPos pos, Direction facing) {
        outputs.pushEnergy(level, pos, facing, sideConfig, energy, PowerGeneration.cap(ArcforgeConfig.COMBUSTION_PLANT_MAX_OUTPUT.getAsInt()));
    }

    @Override
    public int getStored() {
        return energy.getAmountAsInt();
    }

    @Override
    public int getCapacity() {
        return energy.getCapacityAsInt();
    }

    // FE comes out of energy faces (and unsided queries); nothing can put FE in.
    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    // Flue Gas faces (and unsided queries) give out the flue gas; nothing else is a fluid.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null ? flue.getOutput() : flueHandler(mode);
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case GAS -> mode == SideMode.GAS_OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case FLUID, THERMAL -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.combustion_plant");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new BurnerMenu(ModMenuTypes.COMBUSTION_PLANT.get(), containerId, inventory, worldPosition, items, getData());
    }
}
