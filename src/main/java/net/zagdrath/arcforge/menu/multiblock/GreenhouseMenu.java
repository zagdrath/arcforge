/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.zagdrath.arcforge.blockentity.farming.GreenhouseBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Greenhouse Array's GUI (176x206): the FE, water, Nutrient Solution and Carbon Dioxide gauges, the fertilizer slot,
// the beds, speed and temperature, the 3x3 harvest, and a panel with the status and each system's LED.
public class GreenhouseMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_ENERGY_CAPACITY = 1;
    public static final int DATA_ENERGY_USAGE = 2;
    public static final int DATA_WATER = 3;
    public static final int DATA_WATER_CAPACITY = 4;
    public static final int DATA_NUTRIENTS = 5;
    public static final int DATA_NUTRIENTS_CAPACITY = 6;
    public static final int DATA_CO2 = 7;
    public static final int DATA_CO2_CAPACITY = 8;
    public static final int DATA_HEAT = 9;
    public static final int DATA_HEAT_CAPACITY = 10;
    public static final int DATA_HEAT_USAGE = 11;
    // °C x10.
    public static final int DATA_TEMPERATURE = 12;
    public static final int DATA_AMBIENT = 13;
    // Growth speed x100.
    public static final int DATA_SPEED = 14;
    public static final int DATA_BEDS = 15;
    public static final int DATA_GROWING = 16;
    public static final int DATA_LAMPS = 17;
    public static final int DATA_FERTILIZER = 18;
    // GreenhouseBlockEntity.SystemState ordinals.
    public static final int DATA_SYSTEM_WATER = 19;
    public static final int DATA_SYSTEM_NUTRIENTS = 20;
    public static final int DATA_SYSTEM_CO2 = 21;
    public static final int DATA_SYSTEM_HEAT = 22;
    public static final int DATA_SYSTEM_LAMPS = 23;
    public static final int DATA_STATUS = 24;
    public static final int DATA_REDSTONE_MODE = 25;
    public static final int DATA_SIDE_CONFIG = 26;
    public static final int DATA_VALUES = 27;

    public static final int FERTILIZER_X = 76, FERTILIZER_Y = 20;
    public static final int OUTPUT_X = 116, OUTPUT_Y = 20, OUTPUT_PITCH = 18;
    // The player inventory's top row, in the tall (206) GUI.
    public static final int INVENTORY_Y = 124;

    // Client constructor, called with the block position written by the server.
    public GreenhouseMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), GreenhouseBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public GreenhouseMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.GREENHOUSE.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.GREENHOUSE_CONTROLLER.get());
        addMachineSlot(GreenhouseBlockEntity.SLOT_FERTILIZER, FERTILIZER_X, FERTILIZER_Y);
        for (int i = 0; i < GreenhouseBlockEntity.OUTPUT_SLOTS; i++) {
            addMachineSlot(GreenhouseBlockEntity.SLOT_OUTPUT_FIRST + i, OUTPUT_X + (i % 3) * OUTPUT_PITCH, OUTPUT_Y + (i / 3) * OUTPUT_PITCH);
        }
        finish(inventory, INVENTORY_Y);
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

    public int get(int index) {
        return value(index);
    }

    public GreenhouseBlockEntity.SystemState system(int index) {
        return GreenhouseBlockEntity.SystemState.byId(value(index));
    }

    public double getTemperature() {
        return value(DATA_TEMPERATURE) / 10.0;
    }

    public double getAmbient() {
        return value(DATA_AMBIENT) / 10.0;
    }

    public double getSpeed() {
        return value(DATA_SPEED) / 100.0;
    }
}
