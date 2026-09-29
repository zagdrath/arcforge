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
    public static final int DATA_VALUES = 13;

    // Slot positions from the handoff's gui_layouts.json.
    public static final int INPUT_X = 48, INPUT_Y = 35;
    public static final int BYPRODUCT_X = 134, BYPRODUCT_Y = 35;

    // Client constructor, called with the block position written by the server.
    public FermenterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), FermenterBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public FermenterMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.FERMENTER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.FERMENTER.get());
        addMachineSlot(FermenterBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addMachineSlot(FermenterBlockEntity.SLOT_BYPRODUCT, BYPRODUCT_X, BYPRODUCT_Y);
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
}
