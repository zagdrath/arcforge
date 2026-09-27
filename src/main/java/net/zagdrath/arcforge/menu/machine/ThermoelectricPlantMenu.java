/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.machine.ThermoelectricPlantBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// No item slots of its own; just the upgrade slots.
public class ThermoelectricPlantMenu extends MachineMenu {
    public static final int DATA_HEAT = 0;
    public static final int DATA_HEAT_CAPACITY = 1;
    public static final int DATA_TEMPERATURE = 2;
    public static final int DATA_EFFICIENCY = 3;
    public static final int DATA_HEAT_PER_TICK = 4;
    public static final int DATA_ENERGY = 5;
    public static final int DATA_ENERGY_CAPACITY = 6;
    public static final int DATA_FE_PER_TICK = 7;
    public static final int DATA_STATUS = 8;
    public static final int DATA_REDSTONE_MODE = 9;
    public static final int DATA_SIDE_CONFIG = 10;
    public static final int DATA_VALUES = 11;

    // Tabs: Energy, Heat, Redstone, Sides, Upgrades.
    private static final int UPGRADES_TAB = 4;

    // Client constructor, called with the block position written by the server.
    public ThermoelectricPlantMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                ThermoelectricPlantBlockEntity.clientItems(),
                WideIntContainerData.client(DATA_VALUES));
    }

    public ThermoelectricPlantMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.THERMOELECTRIC_PLANT.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.THERMOELECTRIC_PLANT.get());
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

    public int getHeat() {
        return value(DATA_HEAT);
    }

    public int getHeatCapacity() {
        return value(DATA_HEAT_CAPACITY);
    }

    public int getTemperature() {
        return value(DATA_TEMPERATURE);
    }

    // Percent.
    public int getEfficiency() {
        return value(DATA_EFFICIENCY);
    }

    public int getHeatPerTick() {
        return value(DATA_HEAT_PER_TICK);
    }

    public int getEnergy() {
        return value(DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return value(DATA_ENERGY_CAPACITY);
    }

    public int getFePerTick() {
        return value(DATA_FE_PER_TICK);
    }
}
