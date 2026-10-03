/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.machine.SifterBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Sifter: the Metal Press's layout with the mesh slot where the die goes, and a 3x2 grid of finds.
public class SifterMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    public static final int DATA_STATUS = 5;
    public static final int DATA_REDSTONE_MODE = 6;
    public static final int DATA_SIDE_CONFIG = 7;
    public static final int DATA_VALUES = 8;

    public static final int MESH_X = 29, MESH_Y = 35;
    public static final int INPUT_X = 53, INPUT_Y = 35;
    public static final int OUTPUT_X = 102, OUTPUT_Y = 26;

    // Client constructor, called with the block position written by the server.
    public SifterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), SifterBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public SifterMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.SIFTER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.SIFTER.get());
        addMachineSlot(SifterBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addMachineSlot(SifterBlockEntity.SLOT_MESH, MESH_X, MESH_Y);
        for (int i = 0; i < SifterBlockEntity.OUTPUT_SLOTS; i++) {
            addMachineSlot(SifterBlockEntity.FIRST_OUTPUT + i, OUTPUT_X + i % 3 * 18, OUTPUT_Y + i / 3 * 18);
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

    public int getProgress() {
        return value(DATA_PROGRESS);
    }

    public int getTotal() {
        return value(DATA_TOTAL);
    }
}
