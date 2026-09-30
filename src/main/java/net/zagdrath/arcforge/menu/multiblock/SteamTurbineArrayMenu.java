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
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.menu.machine.ElectricPumpMenu;

// Served by the master casing of a Steam Turbine Array; opened from any of its casings or windows.
public class SteamTurbineArrayMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STEAM = 2;
    public static final int DATA_STEAM_AMOUNT = 3;
    public static final int DATA_STEAM_CAPACITY = 4;
    public static final int DATA_FLOW = 5;
    public static final int DATA_MAX_FLOW = 6;
    public static final int DATA_FE_PER_TICK = 7;
    public static final int DATA_RPM = 8;
    public static final int DATA_MAX_RPM = 9;
    public static final int DATA_LENGTH = 10;
    public static final int DATA_STATUS = 11;
    public static final int DATA_REDSTONE_MODE = 12;
    public static final int DATA_SIDE_CONFIG = 13;
    public static final int DATA_LUBRICANT = 14;
    public static final int DATA_LUBRICANT_CAPACITY = 15;
    // What happens to spent steam: EXHAUST_VENTING (no Exhaust port), EXHAUST_VACUUM (draining, with the
    // bonus) or EXHAUST_FULL (its exhaust tank is full, so it vents).
    public static final int DATA_EXHAUST = 16;
    // The lubricant in the tank (a fluid id), for the gauge and its tooltip.
    public static final int DATA_LUBRICANT_FLUID = 17;
    public static final int DATA_VALUES = 18;
    public static final int EXHAUST_VENTING = 0, EXHAUST_VACUUM = 1, EXHAUST_FULL = 2;


    // Client constructor, called with the master's position written by the server.
    public SteamTurbineArrayMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new MachineItemHandler(0, (slot, resource) -> false, SteamTurbineArrayBlockEntity.UPGRADES, () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public SteamTurbineArrayMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.STEAM_TURBINE_ARRAY.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
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

    public int getFePerTick() {
        return value(DATA_FE_PER_TICK);
    }

    public int getLubricant() {
        return value(DATA_LUBRICANT);
    }

    public int getLubricantCapacity() {
        return value(DATA_LUBRICANT_CAPACITY);
    }

    public net.minecraft.world.level.material.Fluid getLubricantFluid() {
        return net.zagdrath.arcforge.menu.machine.ElectricPumpMenu.fluid(value(DATA_LUBRICANT_FLUID));
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

    // One blade set per block between the two ends.
    public int getBladeSets() {
        return Math.max(0, getLength() - 2);
    }

    public int getExhaustState() {
        return value(DATA_EXHAUST);
    }
}
