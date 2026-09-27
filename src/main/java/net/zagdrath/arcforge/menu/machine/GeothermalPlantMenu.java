/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

public class GeothermalPlantMenu extends MachineMenu {
    // Logical data indices (each is a full int, see WideIntContainerData).
    public static final int DATA_HEAT = 0;
    public static final int DATA_HEAT_CAPACITY = 1;
    public static final int DATA_TEMPERATURE = 2;
    public static final int DATA_LAVA = 3;
    public static final int DATA_LAVA_CAPACITY = 4;
    public static final int DATA_BURN_TIME = 5;
    public static final int DATA_BURN_TOTAL = 6;
    public static final int DATA_HEAT_PER_TICK = 7;
    public static final int DATA_LAVA_SOURCES = 8;
    public static final int DATA_MAGMA_BLOCKS = 9;
    public static final int DATA_STATUS = 10;
    public static final int DATA_REDSTONE_MODE = 11;
    public static final int DATA_SIDE_CONFIG = 12;
    public static final int DATA_VALUES = 13;

    // Slot positions from the GUI layout.
    public static final int INPUT_SLOT_X = 31, INPUT_SLOT_Y = 19;
    public static final int OUTPUT_SLOT_X = 31, OUTPUT_SLOT_Y = 53;
    // Tabs: Heat, Redstone, Sides, Upgrades.
    private static final int UPGRADES_TAB = 3;

    // Client constructor, called with the block position written by the server.
    public GeothermalPlantMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new FilteredItemHandler(GeothermalPlantBlockEntity.SLOT_COUNT, GeothermalPlantBlockEntity::isItemValid, () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public GeothermalPlantMenu(int containerId, Inventory inventory, BlockPos pos, FilteredItemHandler items, ContainerData data) {
        super(ModMenuTypes.GEOTHERMAL_PLANT.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.GEOTHERMAL_PLANT.get());
        addMachineSlot(GeothermalPlantBlockEntity.SLOT_INPUT, INPUT_SLOT_X, INPUT_SLOT_Y);
        addMachineSlot(GeothermalPlantBlockEntity.SLOT_OUTPUT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y);
        finish(inventory, GeothermalPlantBlockEntity.SLOT_UPGRADE_FIRST, UPGRADES_TAB);
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
        return slots.get(GeothermalPlantBlockEntity.SLOT_OUTPUT);
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

    public int getLava() {
        return value(DATA_LAVA);
    }

    public int getLavaCapacity() {
        return value(DATA_LAVA_CAPACITY);
    }

    public int getBurnTime() {
        return value(DATA_BURN_TIME);
    }

    public int getBurnTotal() {
        return value(DATA_BURN_TOTAL);
    }

    public int getHeatPerTick() {
        return value(DATA_HEAT_PER_TICK);
    }

    public int getLavaSources() {
        return value(DATA_LAVA_SOURCES);
    }

    public int getMagmaBlocks() {
        return value(DATA_MAGMA_BLOCKS);
    }
}
