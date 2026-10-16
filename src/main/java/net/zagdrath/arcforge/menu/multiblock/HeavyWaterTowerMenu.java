/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.multiblock.HeavyWaterTowerBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Heavy Water Tower's GUI: no slots. Its FE, its three input tanks and its Heavy Water tank, its trays, and the
// progress of the operation every tray is doing.
public class HeavyWaterTowerMenu extends MachineMenu {
    public static final int DATA_STATUS = 0;
    public static final int DATA_FORMED = 1;
    public static final int DATA_REDSTONE_MODE = 2;
    public static final int DATA_SIDE_CONFIG = 3;
    public static final int DATA_TRAYS = 4;
    public static final int DATA_ENERGY = 5;
    public static final int DATA_ENERGY_CAPACITY = 6;
    public static final int DATA_INPUT = 7;          // 3 tanks' amounts
    public static final int DATA_INPUT_FLUID = 10;   // 3 tanks' fluid ids
    public static final int DATA_OUTPUT = 13;
    public static final int DATA_OUTPUT_FLUID = 14;
    public static final int DATA_CAPACITY = 15;
    public static final int DATA_PROGRESS = 16;
    public static final int DATA_TIME = 17;
    public static final int DATA_LAST_MADE = 18;
    public static final int DATA_ENERGY_USE = 19;
    public static final int DATA_VALUES = 20;

    public HeavyWaterTowerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), HeavyWaterTowerBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public HeavyWaterTowerMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.HEAVY_WATER_TOWER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.HEAVY_WATER_TOWER_CONTROLLER.get());
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

    public int get(int index) {
        return value(index);
    }

    public boolean isFormed() {
        return value(DATA_FORMED) != 0;
    }
}
