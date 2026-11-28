/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.machine.ElectricComposterBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class ElectricComposterMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_HEAP = 2;
    public static final int DATA_USAGE = 3;
    public static final int DATA_STATUS = 4;
    public static final int DATA_REDSTONE_MODE = 5;
    public static final int DATA_SIDE_CONFIG = 6;
    public static final int DATA_VALUES = 7;

    // The Seed Extractor's layout without its bonus slot: plant matter, the heap arrow, Compost.
    public static final int INPUT_X = 44, INPUT_Y = 35;
    public static final int OUTPUT_X = 100, OUTPUT_Y = 35;

    // Client constructor, called with the block position written by the server.
    public ElectricComposterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), ElectricComposterBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public ElectricComposterMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.ELECTRIC_COMPOSTER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.ELECTRIC_COMPOSTER.get());
        addMachineSlot(ElectricComposterBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addMachineSlot(ElectricComposterBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y);
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

    public int getHeap() {
        return value(DATA_HEAP);
    }

    public int getUsage() {
        return value(DATA_USAGE);
    }
}
