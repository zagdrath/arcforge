/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.machine.FiberizerBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class FiberizerMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_ENERGY_CAPACITY = 1;
    public static final int DATA_HEAT = 2;
    public static final int DATA_HEAT_CAPACITY = 3;
    public static final int DATA_TEMPERATURE = 4;
    public static final int DATA_MIN_TEMPERATURE = 5;
    public static final int DATA_PROGRESS = 6;
    public static final int DATA_TOTAL = 7;
    public static final int DATA_FE_USAGE = 8;
    public static final int DATA_HEAT_USAGE = 9;
    public static final int DATA_STATUS = 10;
    public static final int DATA_REDSTONE_MODE = 11;
    public static final int DATA_SIDE_CONFIG = 12;
    public static final int DATA_VALUES = 13;

    // Slot positions from fiberizer_gui_layout.json.
    public static final int INPUT_X = 44, INPUT_Y = 35;
    public static final int OUTPUT_X = 101, OUTPUT_Y = 35;
    // Tabs: Energy, Heat, Redstone, Sides, Upgrades.
    private static final int UPGRADES_TAB = 4;

    // Client constructor, called with the block position written by the server.
    public FiberizerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), FiberizerBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public FiberizerMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.FIBERIZER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.FIBERIZER.get());
        addMachineSlot(FiberizerBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addMachineSlot(FiberizerBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y);
        finish(inventory, UPGRADES_TAB);
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

    public int getEnergyCapacity() {
        return value(DATA_ENERGY_CAPACITY);
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

    public int getFeUsage() {
        return value(DATA_FE_USAGE);
    }

    public int getHeatUsage() {
        return value(DATA_HEAT_USAGE);
    }
}
