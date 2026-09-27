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
import net.zagdrath.arcforge.blockentity.machine.ElectricPumpBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class ElectricPumpMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_TOTAL = 3;
    public static final int DATA_USAGE = 4;
    public static final int DATA_FLUID = 5;
    public static final int DATA_FLUID_AMOUNT = 6;
    public static final int DATA_FLUID_CAPACITY = 7;
    public static final int DATA_SOURCE = 8;
    public static final int DATA_INFINITE = 9;
    public static final int DATA_STATUS = 10;
    public static final int DATA_REDSTONE_MODE = 11;
    public static final int DATA_SIDE_CONFIG = 12;
    public static final int DATA_VALUES = 13;

    // Slot positions from electric_pump_gui_layout.json.
    public static final int BUCKET_IN_X = 133, BUCKET_IN_Y = 19;
    public static final int BUCKET_OUT_X = 133, BUCKET_OUT_Y = 53;
    // Tabs: Energy, Redstone, Sides, Upgrades.
    private static final int UPGRADES_TAB = 3;

    // Client constructor, called with the block position written by the server.
    public ElectricPumpMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), ElectricPumpBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public ElectricPumpMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.ELECTRIC_PUMP.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.ELECTRIC_PUMP.get());
        addMachineSlot(ElectricPumpBlockEntity.SLOT_BUCKET_IN, BUCKET_IN_X, BUCKET_IN_Y);
        addMachineSlot(ElectricPumpBlockEntity.SLOT_BUCKET_OUT, BUCKET_OUT_X, BUCKET_OUT_Y);
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

    public Slot getBucketInSlot() {
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

    public Fluid getFluid() {
        return fluid(value(DATA_FLUID));
    }

    public int getFluidAmount() {
        return value(DATA_FLUID_AMOUNT);
    }

    public int getFluidCapacity() {
        return value(DATA_FLUID_CAPACITY);
    }

    // The fluid source below the pump, or Fluids.EMPTY.
    public Fluid getSource() {
        return fluid(value(DATA_SOURCE));
    }

    public boolean isInfiniteSource() {
        return value(DATA_INFINITE) != 0;
    }

    // mB per second: a bucket per cycle.
    public int getRate() {
        int total = getTotal();
        return total > 0 ? 1_000 * 20 / total : 0;
    }

    // A fluid from its synced registry id (-1 or unknown: Fluids.EMPTY).
    public static Fluid fluid(int id) {
        Fluid fluid = id >= 0 ? BuiltInRegistries.FLUID.byId(id) : null;
        return fluid != null ? fluid : Fluids.EMPTY;
    }
}
