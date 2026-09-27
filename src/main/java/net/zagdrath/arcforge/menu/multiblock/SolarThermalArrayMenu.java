/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.multiblock.SolarThermalArrayBlockEntity;
import net.zagdrath.arcforge.heat.SolarModel;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Solar Thermal Array's GUI, opened from any block of a formed tower (served by the controller). It has
// no slots of its own.
public class SolarThermalArrayMenu extends MachineMenu {
    public static final int DATA_HEAT = 0;
    public static final int DATA_HEAT_CAPACITY = 1;
    public static final int DATA_TEMPERATURE = 2;
    public static final int DATA_HEAT_PER_TICK = 3;
    public static final int DATA_SKY_MASK = 4;
    public static final int DATA_WEATHER = 5;
    public static final int DATA_BIOME = 6;
    public static final int DATA_AXIS = 7;
    public static final int DATA_STOWED = 8;
    public static final int DATA_STATUS = 9;
    public static final int DATA_REDSTONE_MODE = 10;
    public static final int DATA_SIDE_CONFIG = 11;
    public static final int DATA_VALUES = 12;

    // Tabs: Heat, Redstone, Ports; the (hidden) upgrade slots would sit under a fourth.
    private static final int UPGRADES_TAB = 3;

    // Client constructor, called with the controller's position written by the server.
    public SolarThermalArrayMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new MachineItemHandler(0, (slot, resource) -> false, SolarThermalArrayBlockEntity.UPGRADES, () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public SolarThermalArrayMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.SOLAR_THERMAL_ARRAY.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.SOLAR_THERMAL_ARRAY_CONTROLLER.get());
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

    // The receiver's temperature (what the sun allows now), in °C.
    public int getTemperature() {
        return value(DATA_TEMPERATURE);
    }

    public int getHeatPerTick() {
        return value(DATA_HEAT_PER_TICK);
    }

    // Whether collector index (north-west, north-east, south-west, south-east) sees the sky.
    public boolean isCollectorLit(int index) {
        return (value(DATA_SKY_MASK) >> index & 1) != 0;
    }

    public int getSkyCount() {
        return Integer.bitCount(value(DATA_SKY_MASK));
    }

    public SolarModel.Weather getWeather() {
        return SolarModel.Weather.byId(value(DATA_WEATHER));
    }

    public double getBiomeMultiplier() {
        return value(DATA_BIOME) / 100.0;
    }

    public boolean isNorthSouth() {
        return value(DATA_AXIS) == 0;
    }

    public boolean isStowed() {
        return value(DATA_STOWED) != 0;
    }
}
