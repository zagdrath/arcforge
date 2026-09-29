/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.blockentity.machine.InfuserBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class InfuserMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_ENERGY_CAPACITY = 1;
    // The tank's fluid as a registry id, -1 when empty.
    public static final int DATA_FLUID = 2;
    public static final int DATA_FLUID_AMOUNT = 3;
    public static final int DATA_FLUID_CAPACITY = 4;
    public static final int DATA_PROGRESS = 5;
    public static final int DATA_TOTAL = 6;
    public static final int DATA_USAGE = 7;
    public static final int DATA_STATUS = 8;
    public static final int DATA_REDSTONE_MODE = 9;
    public static final int DATA_SIDE_CONFIG = 10;
    public static final int DATA_VALUES = 11;

    // Slot positions from infuser_gui_layout.json.
    public static final int BUCKET_IN_X = 43, BUCKET_IN_Y = 19;
    public static final int BUCKET_OUT_X = 43, BUCKET_OUT_Y = 53;
    public static final int INPUT_X = 73, INPUT_Y = 35;
    public static final int OUTPUT_X = 129, OUTPUT_Y = 35;

    // Client constructor, called with the block position written by the server.
    public InfuserMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), InfuserBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public InfuserMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.INFUSER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.INFUSER.get());
        addMachineSlot(InfuserBlockEntity.SLOT_BUCKET_IN, BUCKET_IN_X, BUCKET_IN_Y);
        addMachineSlot(InfuserBlockEntity.SLOT_BUCKET_OUT, BUCKET_OUT_X, BUCKET_OUT_Y);
        addMachineSlot(InfuserBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addMachineSlot(InfuserBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y);
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

    public Fluid getFluid() {
        int id = value(DATA_FLUID);
        return id < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(id);
    }

    public int getFluidAmount() {
        return value(DATA_FLUID_AMOUNT);
    }

    public int getFluidCapacity() {
        return value(DATA_FLUID_CAPACITY);
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
