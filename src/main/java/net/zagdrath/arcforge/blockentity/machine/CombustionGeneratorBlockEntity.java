/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.List;

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
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.BurnerMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.energy.GeneratorEnergyHandler;

// Burns coal, charcoal and coal blocks straight into FE: quick and simple, but only half the FE per
// coal that a Firebox feeding a hot Thermoelectric Plant gets. Fuel burns at twice furnace speed.
public class CombustionGeneratorBlockEntity extends BurnerBlockEntity {
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.ENERGY);

    private final GeneratorEnergyHandler energy;

    public CombustionGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.COMBUSTION_GENERATOR.get(), pos, state,
                new SideConfig(SideMode.INPUT, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new GeneratorEnergyHandler(
                ArcforgeConfig.GENERATOR_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.GENERATOR_MAX_OUTPUT.getAsInt(),
                this::setChanged);
    }

    @Override
    protected double burnSpeed() {
        return ArcforgeConfig.GENERATOR_BURN_SPEED.getAsDouble();
    }

    @Override
    protected int produce() {
        return energy.generate(ArcforgeConfig.GENERATOR_ENERGY_PER_TICK.getAsInt());
    }

    @Override
    protected boolean isBufferFull() {
        return energy.isFull();
    }

    @Override
    protected void pushOutput(ServerLevel level, BlockPos pos, Direction facing) {
        outputs.pushEnergy(level, pos, facing, sideConfig, energy, ArcforgeConfig.GENERATOR_MAX_OUTPUT.getAsInt());
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

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
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
        return Component.translatable("container.arcforge.combustion_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new BurnerMenu(ModMenuTypes.COMBUSTION_GENERATOR.get(), containerId, inventory, worldPosition, items, getData());
    }
}
