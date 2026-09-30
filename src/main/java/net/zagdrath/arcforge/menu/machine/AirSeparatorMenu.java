/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.blockentity.machine.AirSeparatorBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class AirSeparatorMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    // Each gas tank's fluid (a registry id, -1 when empty) and mB.
    public static final int DATA_PRIMARY_FLUID = 5;
    public static final int DATA_PRIMARY = 6;
    public static final int DATA_SECONDARY_FLUID = 7;
    public static final int DATA_SECONDARY = 8;
    public static final int DATA_GAS_CAPACITY = 9;
    // What one operation makes of each (0 where it can't separate air).
    public static final int DATA_RECIPE_PRIMARY = 10;
    public static final int DATA_RECIPE_SECONDARY = 11;
    public static final int DATA_STATUS = 12;
    public static final int DATA_REDSTONE_MODE = 13;
    public static final int DATA_SIDE_CONFIG = 14;
    public static final int DATA_VALUES = 15;

    // Client constructor, called with the block position written by the server.
    public AirSeparatorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), AirSeparatorBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public AirSeparatorMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.AIR_SEPARATOR.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.AIR_SEPARATOR.get());
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

    public int getProgress() {
        return value(DATA_PROGRESS);
    }

    public int getTotal() {
        return value(DATA_TOTAL);
    }

    public Fluid getPrimaryFluid() {
        return ElectricPumpMenu.fluid(value(DATA_PRIMARY_FLUID));
    }

    public int getPrimary() {
        return value(DATA_PRIMARY);
    }

    public Fluid getSecondaryFluid() {
        return ElectricPumpMenu.fluid(value(DATA_SECONDARY_FLUID));
    }

    public int getSecondary() {
        return value(DATA_SECONDARY);
    }

    public int getGasCapacity() {
        return value(DATA_GAS_CAPACITY);
    }

    public int getRecipePrimary() {
        return value(DATA_RECIPE_PRIMARY);
    }

    public int getRecipeSecondary() {
        return value(DATA_RECIPE_SECONDARY);
    }
}
