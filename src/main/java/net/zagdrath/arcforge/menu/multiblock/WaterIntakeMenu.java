/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.multiblock.WaterIntakeBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// Served by the centre of an Industrial Water Intake; opened from any of its casings. It has no slots.
public class WaterIntakeMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_ENERGY_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_WATER = 3;
    public static final int DATA_TANK_CAPACITY = 4;
    // The rate it can pump here, and what it pumped last tick.
    public static final int DATA_RATE = 5;
    public static final int DATA_PUMPED = 6;
    // Connected water sources counted, and whether it's in a river or ocean (1).
    public static final int DATA_WATER_BLOCKS = 7;
    public static final int DATA_OPEN_WATER = 8;
    public static final int DATA_STATUS = 9;
    public static final int DATA_REDSTONE_MODE = 10;
    public static final int DATA_SIDE_CONFIG = 11;
    public static final int DATA_VALUES = 12;

    // Client constructor, called with the centre's position written by the server.
    public WaterIntakeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), WaterIntakeBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public WaterIntakeMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.WATER_INTAKE.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.WATER_INTAKE_CASING.get());
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

    public int getEnergy() {
        return value(DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return value(DATA_ENERGY_CAPACITY);
    }

    public int getUsage() {
        return value(DATA_USAGE);
    }

    public int getWater() {
        return value(DATA_WATER);
    }

    public int getTankCapacity() {
        return value(DATA_TANK_CAPACITY);
    }

    public int getRate() {
        return value(DATA_RATE);
    }

    public int getPumped() {
        return value(DATA_PUMPED);
    }

    public int getWaterBlocks() {
        return value(DATA_WATER_BLOCKS);
    }

    public boolean isOpenWater() {
        return value(DATA_OPEN_WATER) != 0;
    }
}
