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
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.blockentity.machine.ArcMelterBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class ArcMelterMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    public static final int DATA_FLUID = 5;
    public static final int DATA_FLUID_AMOUNT = 6;
    public static final int DATA_FLUID_CAPACITY = 7;
    public static final int DATA_STATUS = 8;
    public static final int DATA_REDSTONE_MODE = 9;
    public static final int DATA_SIDE_CONFIG = 10;
    public static final int DATA_VALUES = 11;

    // Slot position from arc_melter_gui_layout.json.
    public static final int INPUT_X = 44, INPUT_Y = 35;

    // Client constructor, called with the block position written by the server.
    public ArcMelterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), ArcMelterBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public ArcMelterMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.ARC_MELTER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.ARC_MELTER.get());
        addMachineSlot(ArcMelterBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
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

    public int getUsage() {
        return value(DATA_USAGE);
    }

    public int getProgress() {
        return value(DATA_PROGRESS);
    }

    public int getTotal() {
        return value(DATA_TOTAL);
    }

    public Fluid getFluid() {
        return ElectricPumpMenu.fluid(value(DATA_FLUID));
    }

    public int getFluidAmount() {
        return value(DATA_FLUID_AMOUNT);
    }

    public int getFluidCapacity() {
        return value(DATA_FLUID_CAPACITY);
    }
}
