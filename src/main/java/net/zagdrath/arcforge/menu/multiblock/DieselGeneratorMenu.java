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
import net.zagdrath.arcforge.blockentity.multiblock.DieselGeneratorBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.ElectricPumpMenu;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Diesel Generator's GUI, opened from any block of a formed generator (served by the controller): FE, the fuel tank
// and a read-out of its output, speed and fuel use.
public class DieselGeneratorMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_MADE = 2;
    public static final int DATA_MAX_OUTPUT = 3;
    // Speed x 1000.
    public static final int DATA_SPEED = 4;
    // mB/t x 100.
    public static final int DATA_BURNED = 5;
    public static final int DATA_FUEL_FLUID = 6;
    public static final int DATA_FUEL = 7;
    public static final int DATA_FUEL_CAPACITY = 8;
    public static final int DATA_FE_PER_MB = 9;
    public static final int DATA_STATUS = 10;
    public static final int DATA_REDSTONE_MODE = 11;
    public static final int DATA_SIDE_CONFIG = 12;
    public static final int DATA_VALUES = 13;

    // Client constructor, called with the controller's position written by the server.
    public DieselGeneratorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), DieselGeneratorBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public DieselGeneratorMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.DIESEL_GENERATOR.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.DIESEL_GENERATOR_CONTROLLER.get());
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

    public int getMade() {
        return value(DATA_MADE);
    }

    public int getMaxOutput() {
        return value(DATA_MAX_OUTPUT);
    }

    // 0 to 1.
    public float getSpeed() {
        return value(DATA_SPEED) / 1000.0F;
    }

    public double getBurned() {
        return value(DATA_BURNED) / 100.0;
    }

    public Fluid getFuelFluid() {
        return ElectricPumpMenu.fluid(value(DATA_FUEL_FLUID));
    }

    public int getFuel() {
        return value(DATA_FUEL);
    }

    public int getFuelCapacity() {
        return value(DATA_FUEL_CAPACITY);
    }

    public int getFePerMb() {
        return value(DATA_FE_PER_MB);
    }
}
