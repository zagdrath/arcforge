/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.blockentity.multiblock.ChemicalReactorArrayBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// Served by the centre casing of a Chemical Reactor Array; opened from any of its casings (220x222, wider than most so
// each lane's row has room; the inventory is centred): its five tanks along the top round the status screen, one row
// per lane under them.
public class ChemicalReactorArrayMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_STATUS = 3;
    public static final int DATA_REDSTONE_MODE = 4;
    public static final int DATA_SIDE_CONFIG = 5;
    public static final int DATA_TANK_CAPACITY = 6;
    public static final int DATA_HEAT = 7;
    public static final int DATA_TEMPERATURE = 8;
    public static final int DATA_HEAT_USAGE = 9;
    public static final int DATA_PROGRESS_FIRST = 10;
    public static final int DATA_TOTAL_FIRST = DATA_PROGRESS_FIRST + ChemicalReactorArrayBlockEntity.LANES;
    // The five tanks (three inputs, the output, the by-product): fluid registry ids (-1 empty), then amounts.
    public static final int DATA_FLUID_FIRST = DATA_TOTAL_FIRST + ChemicalReactorArrayBlockEntity.LANES;
    public static final int DATA_AMOUNT_FIRST = DATA_FLUID_FIRST + ChemicalReactorArrayBlockEntity.TANKS;
    // The lanes running now, as bits.
    public static final int DATA_RUNNING = DATA_AMOUNT_FIRST + ChemicalReactorArrayBlockEntity.TANKS;
    public static final int DATA_VALUES = DATA_RUNNING + 1;
    public static final int BYPRODUCT_TANK = 4;

    // Each lane's row: its three inputs, then (past the arrow) the output and the by-product.
    public static final int WIDTH = 220, HEIGHT = 222;
    public static final int INPUT_X = 30, OUTPUT_X = 118, BYPRODUCT_X = 136, FIRST_LANE_Y = 74, LANE_PITCH = 18;
    public static final int INVENTORY_X = (WIDTH - 176) / 2 + 8, INVENTORY_Y = 140;

    public ChemicalReactorArrayMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), ChemicalReactorArrayBlockEntity.clientItems(),
                WideIntContainerData.client(DATA_VALUES));
    }

    public ChemicalReactorArrayMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.CHEMICAL_REACTOR_ARRAY.get(), containerId, inventory, pos, items, data, DATA_VALUES,
                ModBlocks.CHEMICAL_REACTOR_ARRAY_CASING.get());
        for (int lane = 0; lane < ChemicalReactorArrayBlockEntity.LANES; lane++) {
            int y = FIRST_LANE_Y + lane * LANE_PITCH;
            for (int i = 0; i < 3; i++) {
                addMachineSlot(ChemicalReactorArrayBlockEntity.slot(lane, ChemicalReactorArrayBlockEntity.INPUT_A + i), INPUT_X + i * 18, y);
            }
            addMachineSlot(ChemicalReactorArrayBlockEntity.slot(lane, ChemicalReactorArrayBlockEntity.OUTPUT), OUTPUT_X, y);
            addMachineSlot(ChemicalReactorArrayBlockEntity.slot(lane, ChemicalReactorArrayBlockEntity.BYPRODUCT), BYPRODUCT_X, y);
        }
        finish(inventory, INVENTORY_X, INVENTORY_Y);
    }

    @Override
    protected int statusIndex() {
        return DATA_STATUS;
    }

    @Override
    protected int redstoneIndex() {
        return DATA_REDSTONE_MODE;
    }

    @Override
    protected int sidesIndex() {
        return DATA_SIDE_CONFIG;
    }

    public int getEnergy() {
        return value(DATA_ENERGY);
    }

    public int getCapacity() {
        return value(DATA_CAPACITY);
    }

    public int getUsage() {
        return value(DATA_USAGE);
    }

    public int getTankCapacity() {
        return value(DATA_TANK_CAPACITY);
    }

    public int getHeat() {
        return value(DATA_HEAT);
    }

    public int getTemperature() {
        return value(DATA_TEMPERATURE);
    }

    public int getHeatUsage() {
        return value(DATA_HEAT_USAGE);
    }

    public int getProgress(int lane) {
        return value(DATA_PROGRESS_FIRST + lane);
    }

    public int getTotal(int lane) {
        return value(DATA_TOTAL_FIRST + lane);
    }

    public boolean isLaneRunning(int lane) {
        return (value(DATA_RUNNING) & (1 << lane)) != 0;
    }

    public Fluid getFluid(int tank) {
        int id = value(DATA_FLUID_FIRST + tank);
        return id < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(id);
    }

    public int getFluidAmount(int tank) {
        return value(DATA_AMOUNT_FIRST + tank);
    }
}
