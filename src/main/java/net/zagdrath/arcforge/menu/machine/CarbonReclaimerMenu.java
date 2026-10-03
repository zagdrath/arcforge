/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.machine.CarbonReclaimerBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Carbon Reclaimer: FE, its operation's cost (spent so far, the whole, and the balance floor), its three tanks.
public class CarbonReclaimerMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_SPENT = 3;
    public static final int DATA_COST = 4;
    public static final int DATA_FLOOR = 5;
    public static final int DATA_CARBON_DIOXIDE = 6;
    public static final int DATA_HYDROGEN = 7;
    public static final int DATA_WATER = 8;
    public static final int DATA_TANK_CAPACITY = 9;
    public static final int DATA_STATUS = 10;
    public static final int DATA_REDSTONE_MODE = 11;
    public static final int DATA_SIDE_CONFIG = 12;
    public static final int DATA_VALUES = 13;

    // Client constructor, called with the block position written by the server.
    public CarbonReclaimerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), CarbonReclaimerBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public CarbonReclaimerMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.CARBON_RECLAIMER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.CARBON_RECLAIMER.get());
        addMachineSlot(CarbonReclaimerBlockEntity.SLOT_OUTPUT, 101, 35);
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

    public int getCapacity() {
        return value(DATA_CAPACITY);
    }

    public int getUsage() {
        return value(DATA_USAGE);
    }

    public int getSpent() {
        return value(DATA_SPENT);
    }

    public int getCost() {
        return value(DATA_COST);
    }

    public int getFloor() {
        return value(DATA_FLOOR);
    }

    public int getCarbonDioxide() {
        return value(DATA_CARBON_DIOXIDE);
    }

    public int getHydrogen() {
        return value(DATA_HYDROGEN);
    }

    public int getWater() {
        return value(DATA_WATER);
    }

    public int getTankCapacity() {
        return value(DATA_TANK_CAPACITY);
    }
}
