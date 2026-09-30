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
import net.zagdrath.arcforge.blockentity.multiblock.BiogasDigesterBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.ElectricPumpMenu;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class BiogasDigesterMenu extends MachineMenu {
    public static final int DATA_WATER = 0;
    public static final int DATA_WATER_CAPACITY = 1;
    public static final int DATA_GAS_FLUID = 2;
    public static final int DATA_GAS = 3;
    public static final int DATA_GAS_CAPACITY = 4;
    public static final int DATA_HEAT = 5;
    public static final int DATA_HEAT_CAPACITY = 6;
    public static final int DATA_TEMPERATURE = 7;
    public static final int DATA_MIN_TEMPERATURE = 8;
    public static final int DATA_HEAT_USAGE = 9;
    // The busy lane nearest done: its progress and length in ticks.
    public static final int DATA_PROGRESS = 10;
    public static final int DATA_TOTAL = 11;
    public static final int DATA_BUSY = 12;
    public static final int DATA_LANES = 13;
    public static final int DATA_STATUS = 14;
    public static final int DATA_REDSTONE_MODE = 15;
    public static final int DATA_SIDE_CONFIG = 16;
    public static final int DATA_VALUES = 17;

    public static final int INPUT_X = 30, INPUT_Y = 26;
    public static final int DIGESTATE_X = 78, DIGESTATE_Y = 26;

    // Client constructor, called with the block position written by the server.
    public BiogasDigesterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), BiogasDigesterBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public BiogasDigesterMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.BIOGAS_DIGESTER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.BIOGAS_DIGESTER_CONTROLLER.get());
        addMachineSlot(BiogasDigesterBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addMachineSlot(BiogasDigesterBlockEntity.SLOT_DIGESTATE, DIGESTATE_X, DIGESTATE_Y);
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

    public int getWater() {
        return value(DATA_WATER);
    }

    public int getWaterCapacity() {
        return value(DATA_WATER_CAPACITY);
    }

    public Fluid getGasFluid() {
        return ElectricPumpMenu.fluid(value(DATA_GAS_FLUID));
    }

    public int getGas() {
        return value(DATA_GAS);
    }

    public int getGasCapacity() {
        return value(DATA_GAS_CAPACITY);
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

    public int getProgress() {
        return value(DATA_PROGRESS);
    }

    public int getTotal() {
        return value(DATA_TOTAL);
    }

    public int getBusy() {
        return value(DATA_BUSY);
    }

    public int getLanes() {
        return value(DATA_LANES);
    }
}
