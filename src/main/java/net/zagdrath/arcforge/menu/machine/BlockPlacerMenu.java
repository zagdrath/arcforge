/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.machine.BlockPlacerBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class BlockPlacerMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    // The slot it will place from next (-1: none), and whether its front is blocked (1).
    public static final int DATA_NEXT_SLOT = 2;
    public static final int DATA_FRONT_BLOCKED = 3;
    public static final int DATA_STATUS = 4;
    public static final int DATA_REDSTONE_MODE = 5;
    public static final int DATA_SIDE_CONFIG = 6;
    public static final int DATA_VALUES = 7;

    // The 3x3 grid from gui_layouts.json.
    public static final int GRID_X = 62, GRID_Y = 17;
    // Tabs: Energy, Redstone, Sides, Upgrades.
    private static final int UPGRADES_TAB = 3;

    // Client constructor, called with the block position written by the server.
    public BlockPlacerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), BlockPlacerBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public BlockPlacerMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.BLOCK_PLACER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.BLOCK_PLACER.get());
        for (int slot = 0; slot < BlockPlacerBlockEntity.SLOTS; slot++) {
            addMachineSlot(slot, GRID_X + (slot % 3) * 18, GRID_Y + (slot / 3) * 18);
        }
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

    public int getCapacity() {
        return value(DATA_CAPACITY);
    }

    public int getNextSlot() {
        return value(DATA_NEXT_SLOT);
    }

    public boolean isFrontBlocked() {
        return value(DATA_FRONT_BLOCKED) != 0;
    }
}
