/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.machine.GrainDryerBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class GrainDryerMenu extends MachineMenu {
    public static final int DATA_HEAT = 0;
    public static final int DATA_HEAT_CAPACITY = 1;
    public static final int DATA_TEMPERATURE = 2;
    public static final int DATA_MIN_TEMPERATURE = 3;
    public static final int DATA_PROGRESS = 4;
    public static final int DATA_TOTAL = 5;
    public static final int DATA_HEAT_USAGE = 6;
    public static final int DATA_STATUS = 7;
    public static final int DATA_REDSTONE_MODE = 8;
    public static final int DATA_SIDE_CONFIG = 9;
    // The fluid tank (Latex), added later.
    public static final int DATA_FLUID = 10;
    public static final int DATA_FLUID_AMOUNT = 11;
    public static final int DATA_FLUID_CAPACITY = 12;
    public static final int DATA_VALUES = 13;

    // The Fiberizer's layout without its FE gauge.
    public static final int INPUT_X = 44, INPUT_Y = 35;
    public static final int OUTPUT_X = 101, OUTPUT_Y = 35;

    // Client constructor, called with the block position written by the server.
    public GrainDryerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), GrainDryerBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public GrainDryerMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.GRAIN_DRYER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.GRAIN_DRYER.get());
        addMachineSlot(GrainDryerBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addMachineSlot(GrainDryerBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y);
        finish(inventory);
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

    public int getHeat() {
        return value(DATA_HEAT);
    }

    public int getHeatCapacity() {
        return value(DATA_HEAT_CAPACITY);
    }

    public int getTemperature() {
        return value(DATA_TEMPERATURE);
    }

    public int getMinTemperature() {
        return value(DATA_MIN_TEMPERATURE);
    }

    public int getProgress() {
        return value(DATA_PROGRESS);
    }

    public int getTotal() {
        return value(DATA_TOTAL);
    }

    public int getHeatUsage() {
        return value(DATA_HEAT_USAGE);
    }

    public net.minecraft.world.level.material.Fluid getFluid() {
        return ElectricPumpMenu.fluid(value(DATA_FLUID));
    }

    public int getFluidAmount() {
        return value(DATA_FLUID_AMOUNT);
    }

    public int getFluidCapacity() {
        return value(DATA_FLUID_CAPACITY);
    }
}
