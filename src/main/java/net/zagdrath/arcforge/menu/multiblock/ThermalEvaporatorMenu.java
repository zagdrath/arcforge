/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.blockentity.multiblock.ThermalEvaporatorBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.ElectricPumpMenu;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class ThermalEvaporatorMenu extends MachineMenu {
    public static final int DATA_INPUT_FLUID = 0;
    public static final int DATA_INPUT = 1;
    public static final int DATA_INPUT_CAPACITY = 2;
    public static final int DATA_OUTPUT_FLUID = 3;
    public static final int DATA_OUTPUT = 4;
    public static final int DATA_OUTPUT_CAPACITY = 5;
    public static final int DATA_WATER = 6;
    public static final int DATA_WATER_CAPACITY = 7;
    public static final int DATA_HEAT = 8;
    public static final int DATA_HEAT_CAPACITY = 9;
    public static final int DATA_TEMPERATURE = 10;
    public static final int DATA_MIN_TEMPERATURE = 11;
    public static final int DATA_HEAT_USAGE = 12;
    // mB/t evaporating, times 100.
    public static final int DATA_RATE = 13;
    // mB towards the next result, and the recipe's mB per result.
    public static final int DATA_PROGRESS = 14;
    public static final int DATA_TOTAL = 15;
    public static final int DATA_STATUS = 16;
    public static final int DATA_REDSTONE_MODE = 17;
    public static final int DATA_SIDE_CONFIG = 18;
    public static final int DATA_VALUES = 19;

    public static final int SALT_X = 54, SALT_Y = 26;

    // Client constructor, called with the block position written by the server.
    public ThermalEvaporatorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), ThermalEvaporatorBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public ThermalEvaporatorMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.THERMAL_EVAPORATOR.get(), containerId, inventory, pos, items, data, DATA_VALUES,
                ModBlocks.THERMAL_EVAPORATOR_CONTROLLER.get());
        addMachineSlot(ThermalEvaporatorBlockEntity.SLOT_SALT, SALT_X, SALT_Y);
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

    public Fluid getInputFluid() {
        return ElectricPumpMenu.fluid(value(DATA_INPUT_FLUID));
    }

    public int getInput() {
        return value(DATA_INPUT);
    }

    public int getInputCapacity() {
        return value(DATA_INPUT_CAPACITY);
    }

    public Fluid getOutputFluid() {
        return ElectricPumpMenu.fluid(value(DATA_OUTPUT_FLUID));
    }

    public int getOutput() {
        return value(DATA_OUTPUT);
    }

    public int getOutputCapacity() {
        return value(DATA_OUTPUT_CAPACITY);
    }

    public int getWater() {
        return value(DATA_WATER);
    }

    public int getWaterCapacity() {
        return value(DATA_WATER_CAPACITY);
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

    public int getHeatUsage() {
        return value(DATA_HEAT_USAGE);
    }

    public double getRate() {
        return value(DATA_RATE) / 100.0;
    }

    public int getProgress() {
        return value(DATA_PROGRESS);
    }

    public int getTotal() {
        return value(DATA_TOTAL);
    }
}
