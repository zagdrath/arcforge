/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.multiblock.BatteryArrayBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Battery Array's GUI: no slots, just its numbers. The FE values are longs, each sent as two ints (low, high).
public class BatteryArrayMenu extends MachineMenu {
    public static final int DATA_STORED_LOW = 0;
    public static final int DATA_STORED_HIGH = 1;
    public static final int DATA_CAPACITY_LOW = 2;
    public static final int DATA_CAPACITY_HIGH = 3;
    // FE that came in and went out during the last tick.
    public static final int DATA_INPUT_LOW = 4;
    public static final int DATA_INPUT_HIGH = 5;
    public static final int DATA_OUTPUT_LOW = 6;
    public static final int DATA_OUTPUT_HIGH = 7;
    // FE/t it may take in and give out (each).
    public static final int DATA_TRANSFER_LOW = 8;
    public static final int DATA_TRANSFER_HIGH = 9;
    public static final int DATA_CELLS = 10;
    public static final int DATA_REGULATORS = 11;
    public static final int DATA_FORMED = 12;
    public static final int DATA_STATUS = 13;
    public static final int DATA_REDSTONE_MODE = 14;
    public static final int DATA_SIDE_CONFIG = 15;
    public static final int DATA_VALUES = 16;

    // The player inventory's top row: the screen is taller than the standard one.
    public static final int INVENTORY_Y = 104;

    // Client constructor, called with the block position written by the server.
    public BatteryArrayMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), BatteryArrayBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public BatteryArrayMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.BATTERY_ARRAY.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.BATTERY_ARRAY_CONTROLLER.get());
        finish(inventory, INVENTORY_Y);
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

    private long wide(int low, int high) {
        return (value(low) & 0xFFFFFFFFL) | ((long) value(high) << 32);
    }

    public long getStored() {
        return wide(DATA_STORED_LOW, DATA_STORED_HIGH);
    }

    public long getCapacity() {
        return wide(DATA_CAPACITY_LOW, DATA_CAPACITY_HIGH);
    }

    public long getInput() {
        return wide(DATA_INPUT_LOW, DATA_INPUT_HIGH);
    }

    public long getOutput() {
        return wide(DATA_OUTPUT_LOW, DATA_OUTPUT_HIGH);
    }

    public long getTransfer() {
        return wide(DATA_TRANSFER_LOW, DATA_TRANSFER_HIGH);
    }

    public int getCells() {
        return value(DATA_CELLS);
    }

    public int getRegulators() {
        return value(DATA_REGULATORS);
    }

    public boolean isFormed() {
        return value(DATA_FORMED) != 0;
    }
}
