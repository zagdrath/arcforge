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
import net.zagdrath.arcforge.blockentity.machine.VitrificationPlantBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class VitrificationPlantMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_ENERGY_CAPACITY = 1;
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

    // The glass, the arrow, the log.
    public static final int GLASS_X = 62, GLASS_Y = 35;
    public static final int OUTPUT_X = 122, OUTPUT_Y = 35;

    public VitrificationPlantMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), VitrificationPlantBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public VitrificationPlantMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.VITRIFICATION_PLANT.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.VITRIFICATION_PLANT.get());
        addMachineSlot(VitrificationPlantBlockEntity.SLOT_GLASS, GLASS_X, GLASS_Y);
        addMachineSlot(VitrificationPlantBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y);
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
