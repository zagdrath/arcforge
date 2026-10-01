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
    // The third input tank (added after the others).
    public static final int DATA_FLUID_C = 15;
    public static final int DATA_AMOUNT_C = 16;
    // The liquid by-product tank (added after the others).
    public static final int DATA_FLUID_BYPRODUCT = 17;
    public static final int DATA_AMOUNT_BYPRODUCT = 18;
    public static final int DATA_VALUES = 19;
    // Tanks 0-2 are the inputs, 3 the output, 4 the by-product.
    public static final int TANKS = 5;
    public static final int OUTPUT_TANK = 3;
    public static final int BYPRODUCT_TANK = 4;

    // Slot positions: the two input slots stack beside the three input tanks, the outputs sit after the arrow.
    public static final int INPUT_X = 71, INPUT_Y = 25;
    public static final int INPUT_B_X = 71, INPUT_B_Y = 45;
    // The output and by-product slots stack after the arrow, like the inputs, leaving room for the two output tanks.
    public static final int OUTPUT_X = 113, OUTPUT_Y = 25;
    public static final int BYPRODUCT_X = 113, BYPRODUCT_Y = 45;

    // Client constructor, called with the block position written by the server.
    public ChemicalReactorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), ChemicalReactorBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public ChemicalReactorMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.CHEMICAL_REACTOR.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.CHEMICAL_REACTOR.get());
        addMachineSlot(ChemicalReactorBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addMachineSlot(ChemicalReactorBlockEntity.SLOT_INPUT_B, INPUT_B_X, INPUT_B_Y);
        addMachineSlot(ChemicalReactorBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y);
        addMachineSlot(ChemicalReactorBlockEntity.SLOT_BYPRODUCT, BYPRODUCT_X, BYPRODUCT_Y);
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

    // Tanks 0, 1 and 2 are the inputs, 3 the output, 4 the by-product.
    public Fluid getFluid(int tank) {
        return ElectricPumpMenu.fluid(value(switch (tank) {
            case 0 -> DATA_FLUID_A;
            case 1 -> DATA_FLUID_B;
            case 2 -> DATA_FLUID_C;
            case BYPRODUCT_TANK -> DATA_FLUID_BYPRODUCT;
            default -> DATA_FLUID_OUT;
        }));
    }

    public int getFluidAmount(int tank) {
        return value(switch (tank) {
            case 0 -> DATA_AMOUNT_A;
            case 1 -> DATA_AMOUNT_B;
            case 2 -> DATA_AMOUNT_C;
            case BYPRODUCT_TANK -> DATA_AMOUNT_BYPRODUCT;
            default -> DATA_AMOUNT_OUT;
        });
    }

    public int getTankCapacity() {
        return value(DATA_TANK_CAPACITY);
    }
}
