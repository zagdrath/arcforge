/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.zagdrath.arcforge.blockentity.machine.InductionFurnaceBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class InductionFurnaceMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_TOTAL = 3;
    public static final int DATA_USAGE = 4;
    public static final int DATA_STATUS = 5;
    public static final int DATA_REDSTONE_MODE = 6;
    public static final int DATA_SIDE_CONFIG = 7;
    public static final int DATA_VALUES = 8;

    // Slot positions from induction_furnace_gui_layout.json.
    public static final int INPUT_X = 53, INPUT_Y = 35;
    public static final int OUTPUT_X = 113, OUTPUT_Y = 35;

    // Client constructor, called with the block position written by the server.
    public InductionFurnaceMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), InductionFurnaceBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES),
                player -> {});
    }

    // onTakeOutput: pays out the stored XP when a player takes smelted items.
    public InductionFurnaceMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data,
            Consumer<Player> onTakeOutput) {
        super(ModMenuTypes.INDUCTION_FURNACE.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.INDUCTION_FURNACE.get());
        addMachineSlot(InductionFurnaceBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addOutputSlot(InductionFurnaceBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y, onTakeOutput);
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

    public Slot getInputSlot() {
        return slots.get(0);
    }

    public int getEnergy() {
        return value(DATA_ENERGY);
    }

    public int getCapacity() {
        return value(DATA_CAPACITY);
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
