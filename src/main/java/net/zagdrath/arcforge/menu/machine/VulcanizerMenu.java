/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.machine.VulcanizerBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class VulcanizerMenu extends MachineMenu {
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
    public static final int DATA_VALUES = 10;

    // The Grain Dryer's layout, with a second input slot (Sulfur) before the first (Raw Rubber).
    public static final int INPUT_X = 44, INPUT_Y = 35;
    public static final int INPUT_B_X = 24, INPUT_B_Y = 35;
    public static final int OUTPUT_X = 101, OUTPUT_Y = 35;

    // Client constructor, called with the block position written by the server.
    public VulcanizerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), VulcanizerBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public VulcanizerMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.VULCANIZER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.VULCANIZER.get());
        addMachineSlot(VulcanizerBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addMachineSlot(VulcanizerBlockEntity.SLOT_INPUT_B, INPUT_B_X, INPUT_B_Y);
        addMachineSlot(VulcanizerBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y);
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
}
