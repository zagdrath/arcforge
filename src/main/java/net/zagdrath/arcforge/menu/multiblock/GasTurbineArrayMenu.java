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
import net.zagdrath.arcforge.blockentity.multiblock.GasTurbineArrayBlockEntity;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.ElectricPumpMenu;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// Served by the master casing of a Gas Turbine Array; opened from any of its casings or windows.
public class GasTurbineArrayMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_FUEL = 2;
    public static final int DATA_FUEL_AMOUNT = 3;
    public static final int DATA_FUEL_CAPACITY = 4;
    // Fuel burned in hundredths of a mB/t, and the throttle in %.
    public static final int DATA_FUEL_MB_X100 = 5;
    public static final int DATA_THROTTLE = 6;
    public static final int DATA_FE_PER_TICK = 7;
    public static final int DATA_RPM = 8;
    public static final int DATA_MAX_RPM = 9;
    public static final int DATA_LENGTH = 10;
    public static final int DATA_STATUS = 11;
    public static final int DATA_REDSTONE_MODE = 12;
    public static final int DATA_SIDE_CONFIG = 13;
    public static final int DATA_LUBRICANT = 14;
    public static final int DATA_LUBRICANT_CAPACITY = 15;
    public static final int DATA_EXHAUST_HU = 16;
    public static final int DATA_EXHAUST_CELSIUS = 17;
    public static final int DATA_VENTED_HU = 18;
    // The tank's fuel in tenths of an FE per mB.
    public static final int DATA_FE_PER_MB_X10 = 19;
    // The lubricant in the tank (a fluid id), for the gauge and its tooltip.
    public static final int DATA_LUBRICANT_FLUID = 20;
    public static final int DATA_VALUES = 21;


    // Client constructor, called with the master's position written by the server.
    public GasTurbineArrayMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new MachineItemHandler(0, (slot, resource) -> false, GasTurbineArrayBlockEntity.UPGRADES, () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public GasTurbineArrayMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.GAS_TURBINE_ARRAY.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.GAS_TURBINE_ARRAY_CASING.get());
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

    public Fluid getFuel() {
        return ElectricPumpMenu.fluid(value(DATA_FUEL));
    }

    public int getFuelAmount() {
        return value(DATA_FUEL_AMOUNT);
    }

    public int getFuelCapacity() {
        return value(DATA_FUEL_CAPACITY);
    }

    public double getFuelPerTick() {
        return value(DATA_FUEL_MB_X100) / 100.0;
    }

    public int getThrottlePercent() {
        return value(DATA_THROTTLE);
    }

    public boolean isThrottled() {
        return getRedstoneMode() == RedstoneMode.THROTTLE;
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

    public int getLubricant() {
        return value(DATA_LUBRICANT);
    }

    public int getLubricantCapacity() {
        return value(DATA_LUBRICANT_CAPACITY);
    }

    public net.minecraft.world.level.material.Fluid getLubricantFluid() {
        return net.zagdrath.arcforge.menu.machine.ElectricPumpMenu.fluid(value(DATA_LUBRICANT_FLUID));
    }

    public int getExhaustHu() {
        return value(DATA_EXHAUST_HU);
    }

    public int getExhaustCelsius() {
        return value(DATA_EXHAUST_CELSIUS);
    }

    public boolean isVenting() {
        return getVentedHu() > 0;
    }

    // Exhaust vented last tick, in HU.
    public int getVentedHu() {
        return value(DATA_VENTED_HU);
    }

    public double getFePerMb() {
        return value(DATA_FE_PER_MB_X10) / 10.0;
    }
}
