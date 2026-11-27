/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.fusion;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.fusion.RemoteHandlingArmBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Remote Handling Arm's GUI (176x186): the FE gauge, the nine spare parts in (blue), the arrow, the nine worn parts
// out (orange, Activated Components), the status line and the parts replaced so far, over the player inventory.
public class RemoteHandlingArmMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_ENERGY_CAPACITY = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_STATUS = 3;
    public static final int DATA_REDSTONE_MODE = 4;
    public static final int DATA_SIDE_CONFIG = 5;
    public static final int DATA_REPLACED = 6;
    public static final int DATA_VALUES = 7;

    public static final int HEIGHT = 186;
    public static final int INPUT_X = 30, OUTPUT_X = 116, GRID_Y = 18;
    public static final int INVENTORY_Y = 104;

    public RemoteHandlingArmMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), RemoteHandlingArmBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public RemoteHandlingArmMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.REMOTE_HANDLING_ARM.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.REMOTE_HANDLING_ARM.get());
        for (int i = 0; i < RemoteHandlingArmBlockEntity.INPUT_SLOTS; i++) {
            addMachineSlot(i, INPUT_X + i % 3 * 18, GRID_Y + i / 3 * 18);
        }
        for (int i = 0; i < RemoteHandlingArmBlockEntity.OUTPUT_SLOTS; i++) {
            addMachineSlot(RemoteHandlingArmBlockEntity.INPUT_SLOTS + i, OUTPUT_X + i % 3 * 18, GRID_Y + i / 3 * 18);
        }
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

    public int getEnergy() {
        return value(DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return value(DATA_ENERGY_CAPACITY);
    }

    public int getProgress() {
        return value(DATA_PROGRESS);
    }

    public int getReplaced() {
        return value(DATA_REPLACED);
    }
}
