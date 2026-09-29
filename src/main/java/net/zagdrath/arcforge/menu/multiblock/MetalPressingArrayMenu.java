/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.zagdrath.arcforge.blockentity.multiblock.MetalPressingArrayBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// Served by the centre casing of a Metal Pressing Array; opened from any of its casings.
public class MetalPressingArrayMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_PRESSING = 3;
    public static final int DATA_POWER_SAVING = 4;
    public static final int DATA_STATUS = 5;
    public static final int DATA_REDSTONE_MODE = 6;
    public static final int DATA_SIDE_CONFIG = 7;
    public static final int DATA_PROGRESS_FIRST = 8;
    public static final int DATA_TOTAL_FIRST = DATA_PROGRESS_FIRST + MetalPressingArrayBlockEntity.LANES;
    public static final int DATA_VALUES = DATA_TOTAL_FIRST + MetalPressingArrayBlockEntity.LANES;

    // Slot positions from metal_pressing_array_gui_layout.json: one row per lane.
    public static final int DIE_X = 27, INPUT_X = 47, OUTPUT_X = 94, FIRST_LANE_Y = 19, LANE_PITCH = 18;

    // Client constructor, called with the centre's position written by the server.
    public MetalPressingArrayMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), MetalPressingArrayBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public MetalPressingArrayMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.METAL_PRESSING_ARRAY.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.METAL_PRESSING_ARRAY_CASING.get());
        for (int lane = 0; lane < MetalPressingArrayBlockEntity.LANES; lane++) {
            int y = FIRST_LANE_Y + lane * LANE_PITCH;
            addMachineSlot(MetalPressingArrayBlockEntity.dieSlot(lane), DIE_X, y);
            addMachineSlot(MetalPressingArrayBlockEntity.inputSlot(lane), INPUT_X, y);
            addMachineSlot(MetalPressingArrayBlockEntity.outputSlot(lane), OUTPUT_X, y);
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

    // The lanes' slots were added die, input, output in lane order.
    public Slot getDieSlot(int lane) {
        return slots.get(lane * 3);
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

    public int getPressing() {
        return value(DATA_PRESSING);
    }

    public int getPowerSaving() {
        return value(DATA_POWER_SAVING);
    }

    public int getProgress(int lane) {
        return value(DATA_PROGRESS_FIRST + lane);
    }

    public int getTotal(int lane) {
        return value(DATA_TOTAL_FIRST + lane);
    }
}
