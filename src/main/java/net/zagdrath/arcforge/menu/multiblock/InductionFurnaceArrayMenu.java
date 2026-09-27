/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.multiblock.InductionFurnaceArrayBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// Served by the centre casing of an Induction Furnace Array; opened from any of its casings.
public class InductionFurnaceArrayMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_SPEED_TENTHS = 3;
    public static final int DATA_POWER_SAVING = 4;
    public static final int DATA_STATUS = 5;
    public static final int DATA_REDSTONE_MODE = 6;
    public static final int DATA_SIDE_CONFIG = 7;
    public static final int DATA_PROGRESS_FIRST = 8;
    public static final int DATA_TOTAL_FIRST = DATA_PROGRESS_FIRST + InductionFurnaceArrayBlockEntity.LANES;
    public static final int DATA_VALUES = DATA_TOTAL_FIRST + InductionFurnaceArrayBlockEntity.LANES;

    // Slot positions from induction_furnace_array_gui_layout.json: one row per lane.
    public static final int INPUT_X = 31, OUTPUT_X = 83, FIRST_LANE_Y = 19, LANE_PITCH = 18;
    // Tabs: Energy, Redstone, Sides, Upgrades.
    private static final int UPGRADES_TAB = 3;

    // Client constructor, called with the centre's position written by the server.
    public InductionFurnaceArrayMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), InductionFurnaceArrayBlockEntity.clientItems(),
                WideIntContainerData.client(DATA_VALUES), player -> {});
    }

    // onTakeOutput: pays out the stored XP when a player takes smelted items.
    public InductionFurnaceArrayMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data,
            Consumer<Player> onTakeOutput) {
        super(ModMenuTypes.INDUCTION_FURNACE_ARRAY.get(), containerId, inventory, pos, items, data, DATA_VALUES,
                ModBlocks.INDUCTION_FURNACE_ARRAY_CASING.get());
        for (int lane = 0; lane < InductionFurnaceArrayBlockEntity.LANES; lane++) {
            int y = FIRST_LANE_Y + lane * LANE_PITCH;
            addMachineSlot(InductionFurnaceArrayBlockEntity.inputSlot(lane), INPUT_X, y);
            addOutputSlot(InductionFurnaceArrayBlockEntity.outputSlot(lane), OUTPUT_X, y, onTakeOutput);
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

    // How much faster each lane is than an Induction Furnace, in tenths (20 = x2).
    public int getSpeedTenths() {
        return value(DATA_SPEED_TENTHS);
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
