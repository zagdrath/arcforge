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
import net.zagdrath.arcforge.blockentity.machine.ElectrolyzerBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class ElectrolyzerMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    public static final int DATA_WATER_FLUID = 5;
    public static final int DATA_WATER = 6;
    public static final int DATA_WATER_CAPACITY = 7;
    public static final int DATA_PRIMARY_FLUID = 8;
    public static final int DATA_PRIMARY = 9;
    public static final int DATA_SECONDARY_FLUID = 10;
    public static final int DATA_SECONDARY = 11;
    public static final int DATA_GAS_CAPACITY = 12;
    // The recipe's amounts (input, primary, secondary; 0 without a recipe), and the FE an operation costs now and
    // at least.
    public static final int DATA_RECIPE_INPUT = 13;
    public static final int DATA_RECIPE_PRIMARY = 14;
    public static final int DATA_RECIPE_SECONDARY = 15;
    public static final int DATA_COST = 16;
    public static final int DATA_FLOOR = 17;
    public static final int DATA_STATUS = 18;
    public static final int DATA_REDSTONE_MODE = 19;
    public static final int DATA_SIDE_CONFIG = 20;
    // Bit 1: venting hydrogen (the primary tank), bit 2: venting oxygen (the secondary).
    public static final int DATA_VENT = 21;
    public static final int DATA_VALUES = 22;

    public static final int BUTTON_VENT_HYDROGEN = 200;
    public static final int BUTTON_VENT_OXYGEN = 201;

    // Tabs: Energy, Redstone, Sides, Upgrades.
    private static final int UPGRADES_TAB = 3;

    // Client constructor, called with the block position written by the server.
    public ElectrolyzerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), ElectrolyzerBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public ElectrolyzerMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.ELECTROLYZER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.ELECTROLYZER.get());
        finish(inventory, UPGRADES_TAB);
    }

    @Override
    public boolean clickMenuButton(net.minecraft.world.entity.player.Player player, int buttonId) {
        if (buttonId == BUTTON_VENT_HYDROGEN || buttonId == BUTTON_VENT_OXYGEN) {
            boolean hydrogen = buttonId == BUTTON_VENT_HYDROGEN;
            access.execute((level, pos) -> {
                if (level.getBlockEntity(pos) instanceof ElectrolyzerBlockEntity electrolyzer) {
                    electrolyzer.setVenting(hydrogen, !(hydrogen ? electrolyzer.isVentingHydrogen() : electrolyzer.isVentingOxygen()));
                }
            });
            return true;
        }
        return super.clickMenuButton(player, buttonId);
    }

    public boolean isVentingHydrogen() {
        return (value(DATA_VENT) & 1) != 0;
    }

    public boolean isVentingOxygen() {
        return (value(DATA_VENT) & 2) != 0;
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

    public Fluid getWaterFluid() {
        return ElectricPumpMenu.fluid(value(DATA_WATER_FLUID));
    }

    public int getWater() {
        return value(DATA_WATER);
    }

    public int getWaterCapacity() {
        return value(DATA_WATER_CAPACITY);
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

    public int getRecipeInput() {
        return value(DATA_RECIPE_INPUT);
    }

    public int getRecipePrimary() {
        return value(DATA_RECIPE_PRIMARY);
    }

    public int getRecipeSecondary() {
        return value(DATA_RECIPE_SECONDARY);
    }

    public int getCost() {
        return value(DATA_COST);
    }

    public int getFloor() {
        return value(DATA_FLOOR);
    }
}
