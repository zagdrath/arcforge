/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.zagdrath.arcforge.blockentity.machine.WasteCompactorBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class WasteCompactorMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_ENERGY_CAPACITY = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_TOTAL = 3;
    public static final int DATA_USAGE = 4;
    public static final int DATA_STATUS = 5;
    public static final int DATA_REDSTONE_MODE = 6;
    public static final int DATA_SIDE_CONFIG = 7;
    public static final int DATA_VALUES = 8;

    // The waste over the empty drum, the arrow, the full drum.
    public static final int INPUT_X = 56, INPUT_Y = 25;
    public static final int DRUM_X = 56, DRUM_Y = 45;
    public static final int OUTPUT_X = 116, OUTPUT_Y = 35;

    public WasteCompactorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), WasteCompactorBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public WasteCompactorMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.WASTE_COMPACTOR.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.WASTE_COMPACTOR.get());
        addMachineSlot(WasteCompactorBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addMachineSlot(WasteCompactorBlockEntity.SLOT_DRUM, DRUM_X, DRUM_Y);
        addMachineSlot(WasteCompactorBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y);
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

    public Slot getSlotFor(int machineSlot) {
        return slots.get(machineSlot);
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

    public int getTotal() {
        return value(DATA_TOTAL);
    }

    public int getUsage() {
        return value(DATA_USAGE);
    }
}
