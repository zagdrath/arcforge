/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.machine.TreeCutterBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class TreeCutterMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    // The area's side, in blocks (5 for a 5x5).
    public static final int DATA_SIZE = 3;
    // Logs in the last tree felled, and trees felled since it loaded.
    public static final int DATA_LAST_LOGS = 4;
    public static final int DATA_FELLED = 5;
    public static final int DATA_STATUS = 6;
    public static final int DATA_REDSTONE_MODE = 7;
    public static final int DATA_SIDE_CONFIG = 8;
    public static final int DATA_VALUES = 9;

    // The Block Breaker's layout: three sapling slots in a row and the fertilizer slot under the first, the 3x3 buffer on
    // the right.
    public static final int SAPLING_X = 30, SAPLING_Y = 18;
    public static final int FERTILIZER_X = 30, FERTILIZER_Y = 40;
    public static final int GRID_X = 98, GRID_Y = 17;

    // Client constructor, called with the block position written by the server.
    public TreeCutterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), TreeCutterBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public TreeCutterMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.TREE_CUTTER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.TREE_CUTTER.get());
        for (int slot = 0; slot < TreeCutterBlockEntity.SAPLING_SLOTS; slot++) {
            addMachineSlot(slot, SAPLING_X + slot * 18, SAPLING_Y);
        }
        addMachineSlot(TreeCutterBlockEntity.SLOT_FERTILIZER, FERTILIZER_X, FERTILIZER_Y);
        for (int slot = 0; slot < TreeCutterBlockEntity.OUTPUT_SLOTS; slot++) {
            addMachineSlot(TreeCutterBlockEntity.FIRST_OUTPUT + slot, GRID_X + (slot % 3) * 18, GRID_Y + (slot / 3) * 18);
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

    public int getSize() {
        return value(DATA_SIZE);
    }

    public int getLastLogs() {
        return value(DATA_LAST_LOGS);
    }

    public int getFelled() {
        return value(DATA_FELLED);
    }
}
