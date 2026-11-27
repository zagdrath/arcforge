/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.fusion;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.fusion.LithiumIsotopeSeparatorBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Lithium Isotope Separator's GUI: the FE gauge, the feed, the progress arrow and the product (the Induction Furnace's
// layout), and the enrichment the product comes out at.
public class LithiumIsotopeSeparatorMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_ENERGY_CAPACITY = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_TOTAL = 3;
    public static final int DATA_USAGE = 4;
    public static final int DATA_STATUS = 5;
    public static final int DATA_REDSTONE_MODE = 6;
    public static final int DATA_SIDE_CONFIG = 7;
    public static final int DATA_NEXT_ASSAY = 8;
    public static final int DATA_VALUES = 9;

    public static final int INPUT_X = 53, INPUT_Y = 35;
    public static final int OUTPUT_X = 113, OUTPUT_Y = 35;

    public LithiumIsotopeSeparatorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), LithiumIsotopeSeparatorBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public LithiumIsotopeSeparatorMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.LITHIUM_ISOTOPE_SEPARATOR.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.LITHIUM_ISOTOPE_SEPARATOR.get());
        addMachineSlot(LithiumIsotopeSeparatorBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addMachineSlot(LithiumIsotopeSeparatorBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y);
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

    public int getNextAssay() {
        return value(DATA_NEXT_ASSAY);
    }
}
