/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.multiblock.HeliumGasTurbineBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// Served by the master casing of a Helium Gas Turbine; opened from any of its casings.
public class HeliumGasTurbineMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_HOT_AMOUNT = 2;
    public static final int DATA_HOT_CAPACITY = 3;
    public static final int DATA_FLOW = 4;
    public static final int DATA_MAX_FLOW = 5;
    // The Hot Helium coming in (mB/t, averaged over about a second).
    public static final int DATA_INFLOW = 6;
    public static final int DATA_FE_PER_TICK = 7;
    public static final int DATA_RPM = 8;
    public static final int DATA_MAX_RPM = 9;
    public static final int DATA_LENGTH = 10;
    public static final int DATA_STATUS = 11;
    public static final int DATA_REDSTONE_MODE = 12;
    public static final int DATA_SIDE_CONFIG = 13;
    public static final int DATA_COOLED_AMOUNT = 14;
    public static final int DATA_COOLED_CAPACITY = 15;
    public static final int DATA_VALUES = 16;

    // Client constructor, called with the master's position written by the server.
    public HeliumGasTurbineMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new MachineItemHandler(0, (slot, resource) -> false, HeliumGasTurbineBlockEntity.UPGRADES, () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public HeliumGasTurbineMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.HELIUM_GAS_TURBINE.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.HELIUM_TURBINE_CASING.get());
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

    public int getHotAmount() {
        return value(DATA_HOT_AMOUNT);
    }

    public int getHotCapacity() {
        return value(DATA_HOT_CAPACITY);
    }

    public int getFlow() {
        return value(DATA_FLOW);
    }

    public int getMaxFlow() {
        return value(DATA_MAX_FLOW);
    }

    public int getInflow() {
        return value(DATA_INFLOW);
    }

    public int getFePerTick() {
        return value(DATA_FE_PER_TICK);
    }

    public int getRpm() {
        return value(DATA_RPM);
    }

    public int getMaxRpm() {
        return value(DATA_MAX_RPM);
    }

    public int getLength() {
        return value(DATA_LENGTH);
    }

    public int getCooledAmount() {
        return value(DATA_COOLED_AMOUNT);
    }

    public int getCooledCapacity() {
        return value(DATA_COOLED_CAPACITY);
    }
}
