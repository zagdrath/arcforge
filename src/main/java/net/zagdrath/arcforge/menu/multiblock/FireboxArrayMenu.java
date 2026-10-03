/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.blockentity.multiblock.FireboxArrayBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.ElectricPumpMenu;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class FireboxArrayMenu extends MachineMenu {
    public static final int DATA_HEAT = 0;
    public static final int DATA_HEAT_CAPACITY = 1;
    public static final int DATA_TEMPERATURE = 2;
    // The temperature of the fuel burning (with oxy-fuel), or 0.
    public static final int DATA_BURN_TEMPERATURE = 3;
    public static final int DATA_HEAT_PER_TICK = 4;
    public static final int DATA_MAX_HEAT_PER_TICK = 5;
    public static final int DATA_FLUID = 6;
    public static final int DATA_FLUID_AMOUNT = 7;
    public static final int DATA_FLUID_CAPACITY = 8;
    public static final int DATA_OXYGEN = 9;
    public static final int DATA_OXYGEN_CAPACITY = 10;
    public static final int DATA_OXY_ACTIVE = 11;
    // The solid fuel burning: its heat left and its whole heat (HU), and the item (-1 for none).
    public static final int DATA_SOLID_LEFT = 12;
    public static final int DATA_SOLID_TOTAL = 13;
    public static final int DATA_BURNING_ITEM = 14;
    public static final int DATA_VOLUME = 15;
    public static final int DATA_STATUS = 16;
    public static final int DATA_REDSTONE_MODE = 17;
    public static final int DATA_SIDE_CONFIG = 18;
    public static final int DATA_VALUES = 19;

    public static final int FUEL_SLOT_X = 31, FUEL_SLOT_Y = 19;

    // Client constructor, called with the block position written by the server.
    public FireboxArrayMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), FireboxArrayBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public FireboxArrayMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.FIREBOX_ARRAY.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.FIREBOX_ARRAY_CONTROLLER.get());
        addMachineSlot(FireboxArrayBlockEntity.SLOT_FUEL, FUEL_SLOT_X, FUEL_SLOT_Y);
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

    public Slot getFuelSlot() {
        return slots.get(0);
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

    public int getBurnTemperature() {
        return value(DATA_BURN_TEMPERATURE);
    }

    public int getHeatPerTick() {
        return value(DATA_HEAT_PER_TICK);
    }

    public int getMaxHeatPerTick() {
        return value(DATA_MAX_HEAT_PER_TICK);
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

    public int getOxygen() {
        return value(DATA_OXYGEN);
    }

    public int getOxygenCapacity() {
        return value(DATA_OXYGEN_CAPACITY);
    }

    public boolean isOxyActive() {
        return value(DATA_OXY_ACTIVE) != 0;
    }

    public int getSolidLeft() {
        return value(DATA_SOLID_LEFT);
    }

    public int getSolidTotal() {
        return value(DATA_SOLID_TOTAL);
    }

    public Item getBurningItem() {
        int id = value(DATA_BURNING_ITEM);
        return id < 0 ? Items.AIR : BuiltInRegistries.ITEM.byId(id);
    }

    public int getVolume() {
        return value(DATA_VOLUME);
    }
}
