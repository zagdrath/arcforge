/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.machine.DiamondPressBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Diamond Press: the Hydrothermal Carbonizer's layout with the FE gauge on the left and no tanks.
public class DiamondPressMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_HEAT = 3;
    public static final int DATA_HEAT_CAPACITY = 4;
    public static final int DATA_TEMPERATURE = 5;
    public static final int DATA_MIN_TEMPERATURE = 6;
    public static final int DATA_HEAT_USAGE = 7;
    public static final int DATA_PROGRESS = 8;
    public static final int DATA_TOTAL = 9;
    public static final int DATA_STATUS = 10;
    public static final int DATA_REDSTONE_MODE = 11;
    public static final int DATA_SIDE_CONFIG = 12;
    public static final int DATA_VALUES = 13;

    public static final int INPUT_X = 44, INPUT_Y = 35;
    public static final int OUTPUT_X = 101, OUTPUT_Y = 35;

    // Client constructor, called with the block position written by the server.
    public DiamondPressMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), DiamondPressBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public DiamondPressMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.DIAMOND_PRESS.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.DIAMOND_PRESS.get());
        addMachineSlot(DiamondPressBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addMachineSlot(DiamondPressBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y);
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

    public int getEnergy() {
        return value(DATA_ENERGY);
    }

    public int getCapacity() {
        return value(DATA_CAPACITY);
    }

    public int getUsage() {
        return value(DATA_USAGE);
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
}
