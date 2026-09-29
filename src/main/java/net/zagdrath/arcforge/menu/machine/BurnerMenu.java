/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.zagdrath.arcforge.blockentity.machine.BurnerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.CombustionPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FireboxBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Combustion Plant's and the Firebox's menu: one fuel slot, and a buffer of FE or heat.
public class BurnerMenu extends MachineMenu {
    public static final int DATA_STORED = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_TEMPERATURE = 2;
    public static final int DATA_BURN_TIME = 3;
    public static final int DATA_BURN_TOTAL = 4;
    public static final int DATA_OUTPUT_PER_TICK = 5;
    public static final int DATA_BURNING_ITEM = 6;
    public static final int DATA_STATUS = 7;
    public static final int DATA_REDSTONE_MODE = 8;
    public static final int DATA_SIDE_CONFIG = 9;
    // Oxy-fuel (the Firebox; 0 for the Combustion Plant).
    public static final int DATA_OXYGEN = 10;
    public static final int DATA_OXYGEN_CAPACITY = 11;
    public static final int DATA_OXY_ACTIVE = 12;
    public static final int DATA_VALUES = 13;

    public static final int FUEL_SLOT_X = 20, FUEL_SLOT_Y = 23;

    // Client constructors, called with the block position written by the server.
    public static BurnerMenu combustionPlant(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        return client(ModMenuTypes.COMBUSTION_PLANT.get(), containerId, inventory, extraData);
    }

    public static BurnerMenu firebox(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        return client(ModMenuTypes.FIREBOX.get(), containerId, inventory, extraData);
    }

    private static BurnerMenu client(MenuType<BurnerMenu> type, int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        return new BurnerMenu(type, containerId, inventory, extraData.readBlockPos(),
                BurnerBlockEntity.clientItems(type == ModMenuTypes.FIREBOX.get() ? FireboxBlockEntity.UPGRADES : CombustionPlantBlockEntity.UPGRADES),
                WideIntContainerData.client(DATA_VALUES));
    }

    public BurnerMenu(MenuType<BurnerMenu> type, int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(type, containerId, inventory, pos, items, data, DATA_VALUES, blockFor(type));
        addMachineSlot(BurnerBlockEntity.SLOT_FUEL, FUEL_SLOT_X, FUEL_SLOT_Y);
        finish(inventory);
    }

    private static Block blockFor(MenuType<BurnerMenu> type) {
        return type == ModMenuTypes.FIREBOX.get() ? ModBlocks.FIREBOX.get() : ModBlocks.COMBUSTION_PLANT.get();
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

    public int getStored() {
        return value(DATA_STORED);
    }

    public int getCapacity() {
        return value(DATA_CAPACITY);
    }

    public int getTemperature() {
        return value(DATA_TEMPERATURE);
    }

    public int getBurnTime() {
        return value(DATA_BURN_TIME);
    }

    public int getBurnTotal() {
        return value(DATA_BURN_TOTAL);
    }

    public int getOutputPerTick() {
        return value(DATA_OUTPUT_PER_TICK);
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

    // The item burning now, or null between items.
    public @Nullable Item getBurningItem() {
        int id = value(DATA_BURNING_ITEM);
        return id < 0 ? null : BuiltInRegistries.ITEM.byId(id);
    }
}
