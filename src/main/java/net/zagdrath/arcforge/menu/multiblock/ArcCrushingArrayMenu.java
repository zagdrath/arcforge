/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.multiblock.ArcCrushingArrayBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// Served by the centre casing of an Arc Crushing Array; opened from any of its casings.
public class ArcCrushingArrayMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_ORE_YIELD = 3;
    public static final int DATA_POWER_SAVING = 4;
    public static final int DATA_STATUS = 5;
    public static final int DATA_REDSTONE_MODE = 6;
    public static final int DATA_SIDE_CONFIG = 7;
    public static final int DATA_PROGRESS_FIRST = 8;
    public static final int DATA_TOTAL_FIRST = DATA_PROGRESS_FIRST + ArcCrushingArrayBlockEntity.LANES;
    public static final int DATA_VALUES = DATA_TOTAL_FIRST + ArcCrushingArrayBlockEntity.LANES;

    // Slot positions from arc_crushing_array_gui_layout.json: one row per lane.
    public static final int INPUT_X = 31, OUTPUT_X = 79, BONUS_X = 99, FIRST_LANE_Y = 19, LANE_PITCH = 18;
    // Tabs: Energy, Redstone, Sides, Upgrades.
    private static final int UPGRADES_TAB = 3;

    // Client constructor, called with the centre's position written by the server.
    public ArcCrushingArrayMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), ArcCrushingArrayBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public ArcCrushingArrayMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.ARC_CRUSHING_ARRAY.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.ARC_CRUSHING_ARRAY_CASING.get());
        for (int lane = 0; lane < ArcCrushingArrayBlockEntity.LANES; lane++) {
            int y = FIRST_LANE_Y + lane * LANE_PITCH;
            addMachineSlot(ArcCrushingArrayBlockEntity.inputSlot(lane), INPUT_X, y);
            addMachineSlot(ArcCrushingArrayBlockEntity.outputSlot(lane), OUTPUT_X, y);
            addMachineSlot(ArcCrushingArrayBlockEntity.bonusSlot(lane), BONUS_X, y);
        }
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

    public int getEnergy() {
        return value(DATA_ENERGY);
    }

    public int getCapacity() {
        return value(DATA_CAPACITY);
    }

    public int getUsage() {
        return value(DATA_USAGE);
    }

    public int getOreYield() {
        return value(DATA_ORE_YIELD);
    }

    public int getPowerSaving() {
        return value(DATA_POWER_SAVING);
    }

    public int getProgress(int lane) {
        return value(DATA_PROGRESS_FIRST + lane);
    }

    public int getTotal(int lane) {
        return value(DATA_TOTAL_FIRST + lane);
    }
}
