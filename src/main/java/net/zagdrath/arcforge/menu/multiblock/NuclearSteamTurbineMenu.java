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
import net.zagdrath.arcforge.blockentity.multiblock.NuclearSteamTurbineBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.ElectricPumpMenu;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// Served by the master casing of a Nuclear Steam Turbine; opened from any of its casings.
public class NuclearSteamTurbineMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STEAM = 2;
    public static final int DATA_STEAM_AMOUNT = 3;
    public static final int DATA_STEAM_CAPACITY = 4;
    public static final int DATA_FLOW = 5;
    public static final int DATA_MAX_FLOW = 6;
    // The least flow it runs on, and the steam coming in (mB/t, averaged over about a second).
    public static final int DATA_MIN_FLOW = 7;
    public static final int DATA_INFLOW = 8;
    public static final int DATA_FE_PER_TICK = 9;
    public static final int DATA_RPM = 10;
    public static final int DATA_MAX_RPM = 11;
    public static final int DATA_LENGTH = 12;
    public static final int DATA_STATUS = 13;
    public static final int DATA_REDSTONE_MODE = 14;
    public static final int DATA_SIDE_CONFIG = 15;
    public static final int DATA_EXHAUST_AMOUNT = 16;
    public static final int DATA_EXHAUST_CAPACITY = 17;
    // 1 with an Exhaust port (else its spent steam vents).
    public static final int DATA_EXHAUST = 18;
    public static final int DATA_TRIPPED = 19;
    public static final int DATA_VALUES = 20;

    // Client constructor, called with the master's position written by the server.
    public NuclearSteamTurbineMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new MachineItemHandler(0, (slot, resource) -> false, NuclearSteamTurbineBlockEntity.UPGRADES, () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public NuclearSteamTurbineMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.NUCLEAR_STEAM_TURBINE.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.NUCLEAR_TURBINE_CASING.get());
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

    public Fluid getSteam() {
        return ElectricPumpMenu.fluid(value(DATA_STEAM));
    }

    public int getSteamAmount() {
        return value(DATA_STEAM_AMOUNT);
    }

    public int getSteamCapacity() {
        return value(DATA_STEAM_CAPACITY);
    }

    public int getFlow() {
        return value(DATA_FLOW);
    }

    public int getMaxFlow() {
        return value(DATA_MAX_FLOW);
    }

    public int getMinFlow() {
        return value(DATA_MIN_FLOW);
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

    public int getExhaustAmount() {
        return value(DATA_EXHAUST_AMOUNT);
    }

    public int getExhaustCapacity() {
        return value(DATA_EXHAUST_CAPACITY);
    }

    public boolean hasExhaust() {
        return value(DATA_EXHAUST) != 0;
    }

    public boolean isTripped() {
        return value(DATA_TRIPPED) != 0;
    }
}
