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
import net.zagdrath.arcforge.blockentity.machine.FuelBurnerBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class FuelBurnerMenu extends MachineMenu {
    public static final int DATA_HEAT = 0;
    public static final int DATA_HEAT_CAPACITY = 1;
    public static final int DATA_TEMPERATURE = 2;
    // The tank's fluid as a registry id, -1 when empty.
    public static final int DATA_FLUID = 3;
    public static final int DATA_FLUID_AMOUNT = 4;
    public static final int DATA_FLUID_CAPACITY = 5;
    public static final int DATA_HEAT_PER_TICK = 6;
    // Fuel burnt this tick, in thousandths of a mB.
    public static final int DATA_BURN_RATE = 7;
    public static final int DATA_STATUS = 8;
    public static final int DATA_REDSTONE_MODE = 9;
    public static final int DATA_SIDE_CONFIG = 10;
    // Oxy-fuel.
    public static final int DATA_OXYGEN = 11;
    public static final int DATA_OXYGEN_CAPACITY = 12;
    public static final int DATA_OXY_ACTIVE = 13;
    public static final int DATA_VALUES = 14;

    // Slot positions from fuel_burner_gui_layout.json (the Geothermal Plant's frame).
    public static final int BUCKET_IN_X = 31, BUCKET_IN_Y = 19;
    public static final int BUCKET_OUT_X = 31, BUCKET_OUT_Y = 53;

    // Client constructor, called with the block position written by the server.
    public FuelBurnerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), FuelBurnerBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public FuelBurnerMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.FUEL_BURNER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.FUEL_BURNER.get());
        addMachineSlot(FuelBurnerBlockEntity.SLOT_BUCKET_IN, BUCKET_IN_X, BUCKET_IN_Y);
        addMachineSlot(FuelBurnerBlockEntity.SLOT_BUCKET_OUT, BUCKET_OUT_X, BUCKET_OUT_Y);
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

    public Slot getOutputSlot() {
        return slots.get(FuelBurnerBlockEntity.SLOT_BUCKET_OUT);
    }

    public int getHeat() {
        return value(DATA_HEAT);
    }

    public int getHeatCapacity() {
        return value(DATA_HEAT_CAPACITY);
    }

    public int getTemperature() {
        return value(DATA_TEMPERATURE);
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

    public int getHeatPerTick() {
        return value(DATA_HEAT_PER_TICK);
    }

    public int getOxygen() {
        return value(DATA_OXYGEN);
    }

    public int getOxygenCapacity() {
        return value(DATA_OXYGEN_CAPACITY);
    }

    public boolean isOxyActive() {
        return value(DATA_OXY_ACTIVE) != 0;
    }

    // mB burnt per tick.
    public double getBurnRate() {
        return value(DATA_BURN_RATE) / 1_000.0;
    }
}
