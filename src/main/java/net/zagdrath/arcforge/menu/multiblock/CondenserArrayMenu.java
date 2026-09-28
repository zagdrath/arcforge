/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.blockentity.multiblock.CondenserArrayBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// Served by the centre of a Condenser Array; opened from any of its casings. It has no slots.
public class CondenserArrayMenu extends MachineMenu {
    public static final int DATA_EXHAUST = 0;
    public static final int DATA_WATER = 1;
    public static final int DATA_TANK_CAPACITY = 2;
    // The rate it can condense at, and what it condensed last tick.
    public static final int DATA_RATE = 3;
    public static final int DATA_CONDENSED = 4;
    // The rate's parts: open air, water and ice touching it (mB/t), and the climate multiplier x 100.
    public static final int DATA_AIR = 5;
    public static final int DATA_WATER_BONUS = 6;
    public static final int DATA_ICE = 7;
    public static final int DATA_MULTIPLIER = 8;
    public static final int DATA_STATUS = 9;
    public static final int DATA_REDSTONE_MODE = 10;
    public static final int DATA_SIDE_CONFIG = 11;
    public static final int DATA_VALUES = 12;

    // No slots: the upgrade tab index only has to be past the other tabs.
    private static final int UPGRADES_TAB = 3;

    // Client constructor, called with the centre's position written by the server.
    public CondenserArrayMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), CondenserArrayBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public CondenserArrayMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.CONDENSER_ARRAY.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.CONDENSER_ARRAY_CASING.get());
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

    public int getExhaust() {
        return value(DATA_EXHAUST);
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

    public int getCondensed() {
        return value(DATA_CONDENSED);
    }

    public int getAir() {
        return value(DATA_AIR);
    }

    public int getWaterBonus() {
        return value(DATA_WATER_BONUS);
    }

    public int getIce() {
        return value(DATA_ICE);
    }

    public int getMultiplierPercent() {
        return value(DATA_MULTIPLIER);
    }

    // For the tank tooltips.
    public static Fluid exhaustFluid() {
        return ModFluids.EXHAUST_STEAM.get();
    }
}
