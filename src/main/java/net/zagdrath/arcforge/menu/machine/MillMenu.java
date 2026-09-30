/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.machine.MillBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class MillMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_STATUS = 3;
    public static final int DATA_REDSTONE_MODE = 4;
    public static final int DATA_SIDE_CONFIG = 5;
    public static final int DATA_PROGRESS_FIRST = 6;
    public static final int DATA_TOTAL_FIRST = DATA_PROGRESS_FIRST + MillBlockEntity.LANES;
    public static final int DATA_VALUES = DATA_TOTAL_FIRST + MillBlockEntity.LANES;

    // One row per lane: input, arrow, result, bonus (the Arc Crushing Array's layout).
    public static final int INPUT_X = 31, OUTPUT_X = 79, BONUS_X = 99, FIRST_LANE_Y = 19, LANE_PITCH = 18;

    // Client constructor, called with the block position written by the server.
    public MillMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), MillBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public MillMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.MILL.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.MILL.get());
        for (int lane = 0; lane < MillBlockEntity.LANES; lane++) {
            int y = FIRST_LANE_Y + lane * LANE_PITCH;
            addMachineSlot(MillBlockEntity.inputSlot(lane), INPUT_X, y);
            addMachineSlot(MillBlockEntity.outputSlot(lane), OUTPUT_X, y);
            addMachineSlot(MillBlockEntity.bonusSlot(lane), BONUS_X, y);
        }
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

    public int getProgress(int lane) {
        return value(DATA_PROGRESS_FIRST + lane);
    }

    public int getTotal(int lane) {
        return value(DATA_TOTAL_FIRST + lane);
    }
}
