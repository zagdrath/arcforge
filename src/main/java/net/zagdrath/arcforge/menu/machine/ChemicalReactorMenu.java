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
import net.zagdrath.arcforge.blockentity.machine.ChemicalReactorBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class ChemicalReactorMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    // Each tank's fluid (a registry id, -1 when empty) and mB.
    public static final int DATA_FLUID_A = 5;
    public static final int DATA_AMOUNT_A = 6;
    public static final int DATA_FLUID_B = 7;
    public static final int DATA_AMOUNT_B = 8;
    public static final int DATA_FLUID_OUT = 9;
    public static final int DATA_AMOUNT_OUT = 10;
    public static final int DATA_TANK_CAPACITY = 11;
    public static final int DATA_STATUS = 12;
    public static final int DATA_REDSTONE_MODE = 13;
    public static final int DATA_SIDE_CONFIG = 14;
    public static final int DATA_VALUES = 15;

    // Slot positions from chemical_reactor_gui_layout.json.
    public static final int INPUT_X = 60, INPUT_Y = 35;
    public static final int OUTPUT_X = 108, OUTPUT_Y = 35;
    public static final int BYPRODUCT_X = 130, BYPRODUCT_Y = 35;
    // Tabs: Energy, Redstone, Sides, Upgrades.
    private static final int UPGRADES_TAB = 3;

    // Client constructor, called with the block position written by the server.
    public ChemicalReactorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), ChemicalReactorBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public ChemicalReactorMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.CHEMICAL_REACTOR.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.CHEMICAL_REACTOR.get());
        addMachineSlot(ChemicalReactorBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addMachineSlot(ChemicalReactorBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y);
        addMachineSlot(ChemicalReactorBlockEntity.SLOT_BYPRODUCT, BYPRODUCT_X, BYPRODUCT_Y);
        finish(inventory, UPGRADES_TAB);
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

    // Tank 0 and 1 are the inputs, 2 the output.
    public Fluid getFluid(int tank) {
        return ElectricPumpMenu.fluid(value(tank == 0 ? DATA_FLUID_A : tank == 1 ? DATA_FLUID_B : DATA_FLUID_OUT));
    }

    public int getFluidAmount(int tank) {
        return value(tank == 0 ? DATA_AMOUNT_A : tank == 1 ? DATA_AMOUNT_B : DATA_AMOUNT_OUT);
    }

    public int getTankCapacity() {
        return value(DATA_TANK_CAPACITY);
    }
}
