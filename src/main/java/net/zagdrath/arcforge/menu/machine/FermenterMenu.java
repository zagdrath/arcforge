/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.blockentity.machine.FermenterBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class FermenterMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    public static final int DATA_WATER = 5;
    public static final int DATA_WATER_CAPACITY = 6;
    public static final int DATA_ETHANOL_FLUID = 7;
    public static final int DATA_ETHANOL = 8;
    public static final int DATA_ETHANOL_CAPACITY = 9;
    public static final int DATA_STATUS = 10;
    public static final int DATA_REDSTONE_MODE = 11;
    public static final int DATA_SIDE_CONFIG = 12;
    public static final int DATA_CO2 = 13;
    public static final int DATA_CO2_CAPACITY = 14;
    // Operations the Dried Hops already taken still boost.
    public static final int DATA_ADDITIVE_LEFT = 15;
    public static final int DATA_VALUES = 16;

    // Slot positions: the two inputs over the additive, left of the arrow; the output after it; the tanks on the right.
    public static final int INPUT_X = 44, INPUT_Y = 17;
    public static final int INPUT_2_X = 44, INPUT_2_Y = 35;
    public static final int ADDITIVE_X = 44, ADDITIVE_Y = 53;
    public static final int BYPRODUCT_X = 94, BYPRODUCT_Y = 35;
    // The additive slot's index among the menu's slots (added third).
    public static final int ADDITIVE_SLOT_INDEX = 2;
    // The second input slot's index among the menu's slots (added fourth).
    public static final int INPUT_2_SLOT_INDEX = 3;

    // Client constructor, called with the block position written by the server.
    public FermenterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), FermenterBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public FermenterMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.FERMENTER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.FERMENTER.get());
        addMachineSlot(FermenterBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addMachineSlot(FermenterBlockEntity.SLOT_BYPRODUCT, BYPRODUCT_X, BYPRODUCT_Y);
        addMachineSlot(FermenterBlockEntity.SLOT_ADDITIVE, ADDITIVE_X, ADDITIVE_Y);
        addMachineSlot(FermenterBlockEntity.SLOT_INPUT_2, INPUT_2_X, INPUT_2_Y);
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

    public int getWater() {
        return value(DATA_WATER);
    }

    public int getWaterCapacity() {
        return value(DATA_WATER_CAPACITY);
    }

    public Fluid getEthanolFluid() {
        return ElectricPumpMenu.fluid(value(DATA_ETHANOL_FLUID));
    }

    public int getEthanol() {
        return value(DATA_ETHANOL);
    }

    public int getEthanolCapacity() {
        return value(DATA_ETHANOL_CAPACITY);
    }

    public int getCarbonDioxide() {
        return value(DATA_CO2);
    }

    public int getCarbonDioxideCapacity() {
        return value(DATA_CO2_CAPACITY);
    }

    public int getAdditiveLeft() {
        return value(DATA_ADDITIVE_LEFT);
    }
}
